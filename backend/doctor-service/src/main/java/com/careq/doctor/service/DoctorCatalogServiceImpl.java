package com.careq.doctor.service;

import com.careq.doctor.dto.AvailabilityRequestDto;
import com.careq.doctor.dto.DoctorCatalogRequestDto;
import com.careq.doctor.dto.DoctorCatalogResponseDto;
import com.careq.doctor.dto.DoctorSearchResultDto;
import com.careq.doctor.entity.Department;
import com.careq.doctor.entity.DoctorCatalogEntry;
import com.careq.doctor.exception.DepartmentNotFoundException;
import com.careq.doctor.exception.DoctorCatalogNotFoundException;
import com.careq.doctor.exception.DuplicateDoctorCatalogEntryException;
import com.careq.doctor.repository.DepartmentRepository;
import com.careq.doctor.repository.DoctorCatalogRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import static java.util.stream.Collectors.toList;

@Service
public class DoctorCatalogServiceImpl implements DoctorCatalogService {

    /** Whitelist of sortable properties — the entity has no more than these. */
    private static final Map<String, String> SORTABLE_PROPERTIES = Map.of(
            "name", "name",
            "consultationFee", "consultationFee",
            "experienceYears", "experienceYears"
    );

    private final DoctorCatalogRepository doctorCatalogRepository;
    private final DepartmentRepository departmentRepository;

    /** Upper bound for page size, so a client can never request unbounded pages. */
    private final int maxPageSize;

    public DoctorCatalogServiceImpl(DoctorCatalogRepository doctorCatalogRepository,
                                    DepartmentRepository departmentRepository,
                                    @Value("${doctor-list.max-page-size:100}") int maxPageSize) {
        this.doctorCatalogRepository = doctorCatalogRepository;
        this.departmentRepository = departmentRepository;
        this.maxPageSize = maxPageSize;
    }

    @Override
    @Transactional(readOnly = true)
    // cached in Redis (60s TTL, keyed by every filter/pagination param).
    // Live availability is deliberately part of this cache — a doctor going
    // offline is still reflected within 60s, and the queue join path always
    // re-reads availability uncached via getDoctorById (never blocked by this).
    @Cacheable(cacheNames = "doctorCatalog", key = "{#departmentId, #specialization, #search, #page, #size, #sortBy, #sortDirection}")
    public Page<DoctorCatalogResponseDto> getAllDoctors(Long departmentId, String specialization, String search,
                                                        int page, int size, String sortBy, String sortDirection) {
        String property = SORTABLE_PROPERTIES.getOrDefault(
                sortBy == null ? "name" : sortBy, "name");
        Sort.Direction direction = "desc".equalsIgnoreCase(sortDirection)
                ? Sort.Direction.DESC : Sort.Direction.ASC;
        PageRequest pageable = PageRequest.of(Math.max(page, 0),
                Math.min(Math.max(size, 1), maxPageSize), Sort.by(direction, property));

        String trimmedSearch = search == null ? null : search.trim();
        String trimmedSpecialization = specialization == null ? null : specialization.trim();

        return doctorCatalogRepository
                .search(departmentId, trimmedSpecialization, trimmedSearch, pageable)
                .map(entry -> DoctorCatalogResponseDto.fromEntity(entry, getDepartmentName(entry.getDepartmentId())));
    }

    @Override
    @Transactional(readOnly = true)
    // NOT cached on purpose — the ranked search feeds the chat assistant and
    // the browse screen, so it always reflects the latest catalog + availability.
    public List<DoctorSearchResultDto> searchRanked(String query, boolean availableOnly, int limit) {
        if (query == null || query.isBlank()) {
            return List.of();
        }
        int cappedLimit = Math.min(Math.max(limit, 1), maxPageSize);
        List<String> tokens = tokenize(query);
        if (tokens.isEmpty()) {
            return List.of();
        }

        List<DoctorSearchResultDto> results = new ArrayList<>();
        for (DoctorCatalogEntry entry : doctorCatalogRepository.findAll()) {
            if (availableOnly && !Boolean.TRUE.equals(entry.getIsAvailable())) {
                continue;
            }
            String departmentName = getDepartmentName(entry.getDepartmentId());
            int score = relevanceScore(entry, departmentName, tokens);
            if (score > 0) {
                results.add(new DoctorSearchResultDto(
                        DoctorCatalogResponseDto.fromEntity(entry, departmentName), score));
            }
        }

        results.sort(Comparator.comparingInt(DoctorSearchResultDto::getRelevanceScore).reversed()
                .thenComparing(DoctorSearchResultDto::getName, Comparator.nullsLast(String::compareToIgnoreCase)));
        return results.stream().limit(cappedLimit).collect(toList());
    }

    /**
     * Scores one entry against the query tokens. Weighted fields: name ×3,
     * specialization ×2, department ×2, qualification ×1. Within a field an
     * exact whole-token match scores highest, then a prefix match, then a
     * substring. Normalized to 0-100.
     */
    private int relevanceScore(DoctorCatalogEntry entry, String departmentName, List<String> tokens) {
        // List of (field text, weight) pairs — NOT a Map, because two fields
        // can normalize to the same string (e.g. specialization and department
        // both "cardiology") and must both be scored.
        List<Map.Entry<String, Integer>> fields = List.of(
                Map.entry(lower(entry.getName()), 3),
                Map.entry(lower(entry.getSpecialization()), 2),
                Map.entry(lower(departmentName), 2),
                Map.entry(lower(entry.getQualification()), 1)
        );

        int raw = 0;
        for (String token : tokens) {
            for (Map.Entry<String, Integer> field : fields) {
                String text = field.getKey();
                if (text == null || text.isEmpty()) {
                    continue;
                }
                if (containsToken(text, token)) {
                    raw += 6 * field.getValue();
                } else if (text.startsWith(token)) {
                    raw += 4 * field.getValue();
                } else if (text.contains(token)) {
                    raw += 2 * field.getValue();
                }
            }
        }
        return Math.min(100, raw);
    }

    private boolean containsToken(String text, String token) {
        int idx = text.indexOf(token);
        while (idx >= 0) {
            boolean beforeOk = idx == 0 || !Character.isLetterOrDigit(text.charAt(idx - 1));
            int after = idx + token.length();
            boolean afterOk = after >= text.length() || !Character.isLetterOrDigit(text.charAt(after));
            if (beforeOk && afterOk) {
                return true;
            }
            idx = text.indexOf(token, idx + 1);
        }
        return false;
    }

    private List<String> tokenize(String query) {
        String[] parts = query.toLowerCase(Locale.ROOT).split("[^a-z0-9]+");
        List<String> tokens = new ArrayList<>();
        for (String part : parts) {
            if (!part.isBlank()) {
                tokens.add(part);
            }
        }
        return tokens;
    }

    private String lower(String value) {
        return value == null ? "" : value.toLowerCase(Locale.ROOT);
    }

    @Override
    @Transactional(readOnly = true)
    // NOT cached on purpose — queue-service reads isAvailable/avgConsultTime
    // live from this endpoint (single source of truth, join validation).
    public DoctorCatalogResponseDto getDoctorById(Long id) {
        DoctorCatalogEntry entry = doctorCatalogRepository.findById(id)
                .orElseThrow(() -> new DoctorCatalogNotFoundException(
                        "Doctor catalog entry not found with id: " + id));
        return DoctorCatalogResponseDto.fromEntity(entry, getDepartmentName(entry.getDepartmentId()));
    }

    @Override
    @Transactional
    @CacheEvict(cacheNames = "doctorCatalog", allEntries = true)
    public DoctorCatalogResponseDto createDoctor(DoctorCatalogRequestDto request) {
        if (doctorCatalogRepository.existsByUserId(request.getUserId())) {
            throw new DuplicateDoctorCatalogEntryException(
                    "Doctor catalog entry already exists for userId: " + request.getUserId());
        }

        // Validate department exists
        if (!departmentRepository.existsById(request.getDepartmentId())) {
            throw new DepartmentNotFoundException(
                    "Department not found with id: " + request.getDepartmentId());
        }

        DoctorCatalogEntry entry = new DoctorCatalogEntry();
        entry.setName(request.getName().trim());
        entry.setUserId(request.getUserId());
        entry.setDepartmentId(request.getDepartmentId());
        entry.setSpecialization(request.getSpecialization().trim());
        entry.setQualification(request.getQualification().trim());
        entry.setExperienceYears(request.getExperienceYears());
        entry.setConsultationFee(request.getConsultationFee());
        entry.setAvgConsultationTimeMinutes(request.getAvgConsultationTimeMinutes());
        entry.setIsAvailable(true);

        entry = doctorCatalogRepository.save(entry);
        return DoctorCatalogResponseDto.fromEntity(entry, getDepartmentName(entry.getDepartmentId()));
    }

    @Override
    @Transactional
    @CacheEvict(cacheNames = "doctorCatalog", allEntries = true)
    public DoctorCatalogResponseDto updateDoctor(Long id, DoctorCatalogRequestDto request) {
        DoctorCatalogEntry entry = doctorCatalogRepository.findById(id)
                .orElseThrow(() -> new DoctorCatalogNotFoundException(
                        "Doctor catalog entry not found with id: " + id));

        // Validate department exists
        if (!departmentRepository.existsById(request.getDepartmentId())) {
            throw new DepartmentNotFoundException(
                    "Department not found with id: " + request.getDepartmentId());
        }

        entry.setName(request.getName().trim());
        entry.setDepartmentId(request.getDepartmentId());
        entry.setSpecialization(request.getSpecialization().trim());
        entry.setQualification(request.getQualification().trim());
        entry.setExperienceYears(request.getExperienceYears());
        entry.setConsultationFee(request.getConsultationFee());
        entry.setAvgConsultationTimeMinutes(request.getAvgConsultationTimeMinutes());

        entry = doctorCatalogRepository.save(entry);
        return DoctorCatalogResponseDto.fromEntity(entry, getDepartmentName(entry.getDepartmentId()));
    }

    @Override
    @Transactional
    @CacheEvict(cacheNames = "doctorCatalog", allEntries = true)
    public void deleteDoctor(Long id) {
        if (!doctorCatalogRepository.existsById(id)) {
            throw new DoctorCatalogNotFoundException(
                    "Doctor catalog entry not found with id: " + id);
        }
        doctorCatalogRepository.deleteById(id);
    }

    @Override
    @Transactional
    // Availability is rendered in the cached list — evict so the doctor's own
    // toggle is reflected immediately (other entries stale for max 60s).
    @CacheEvict(cacheNames = "doctorCatalog", allEntries = true)
    public DoctorCatalogResponseDto toggleAvailability(String userId, AvailabilityRequestDto request) {
        DoctorCatalogEntry entry = doctorCatalogRepository.findByUserId(userId)
                .orElseThrow(() -> new DoctorCatalogNotFoundException(
                        "No doctor catalog entry found for userId: " + userId));

        entry.setIsAvailable(request.getIsAvailable());
        entry = doctorCatalogRepository.save(entry);
        return DoctorCatalogResponseDto.fromEntity(entry, getDepartmentName(entry.getDepartmentId()));
    }

    @Override
    @Transactional(readOnly = true)
    public DoctorCatalogResponseDto getMyDoctor(String userId) {
        DoctorCatalogEntry entry = doctorCatalogRepository.findByUserId(userId)
                .orElseThrow(() -> new DoctorCatalogNotFoundException(
                        "No doctor catalog entry found for your account. Ask an admin to create one in Admin → Manage Doctors."));
        return DoctorCatalogResponseDto.fromEntity(entry, getDepartmentName(entry.getDepartmentId()));
    }

    private String getDepartmentName(Long departmentId) {
        return departmentRepository.findById(departmentId)
                .map(Department::getName)
                .orElse("Unknown");
    }
}
