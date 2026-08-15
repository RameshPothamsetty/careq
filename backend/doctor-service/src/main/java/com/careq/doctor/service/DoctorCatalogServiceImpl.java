package com.careq.doctor.service;

import com.careq.doctor.dto.AvailabilityRequestDto;
import com.careq.doctor.dto.DoctorCatalogRequestDto;
import com.careq.doctor.dto.DoctorCatalogResponseDto;
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

import java.util.Map;

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
