package com.careq.queue.service;

import com.careq.queue.client.DoctorServiceClient;
import com.careq.queue.dto.AiAssessment;
import com.careq.queue.dto.DoctorCatalogResponseDto;
import com.careq.queue.dto.DoctorSuggestionDto;
import com.careq.queue.dto.DoctorSuggestionResponseDto;
import com.careq.queue.entity.QueueEntry;
import com.careq.queue.entity.TriageLevel;
import com.careq.queue.exception.DoctorServiceUnavailableException;
import com.careq.queue.repository.QueueEntryRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * AI doctor recommendation (Phase 1 of closing the patient-to-doctor gap).
 *
 * Given free-text symptoms, this service:
 *   1. Asks the LLM for the urgency level AND the most likely department
 *      (extending the existing triage call — one call, dual purpose).
 *   2. Ranks AVAILABLE doctors by relevance to that department/specialty.
 *   3. Breaks ties by the live predicted wait a new joiner would face
 *      (shortest first), so the recommendation is not just correct but fast.
 *
 * Guardrails:
 *   - The AI only RECOMMENDS — the patient confirms before joining.
 *   - Never a hard failure: no GROQ_API_KEY / LLM failure degrades to keyword
 *     matching against specialty + department names; no matched specialty
 *     degrades to "nearest available doctor" by wait; doctor-service being
 *     down surfaces the existing graceful 503.
 */
@Service
public class DoctorRecommendationService {

    private static final Logger log = LoggerFactory.getLogger(DoctorRecommendationService.class);

    /** How many suggestions the patient sees. */
    private static final int TOP_N = 3;

    /**
     * Curated symptom-keyword → department hints, used ONLY on the degraded
     * path (no LLM key / LLM failure). Covers common OPD complaints so a
     * patient describing "heart palpitations" still lands on Cardiology even
     * when the model is unavailable. Never the primary signal — the LLM is.
     *
     * LinkedHashMap: INSERTION order defines priority, so a symptom matching
     * several departments (e.g. "chest pain and fever") resolves deterministically
     * instead of depending on hash iteration order.
     */
    private static final Map<String, List<String>> SYMPTOM_DEPARTMENT_KEYWORDS;

    static {
        Map<String, List<String>> m = new LinkedHashMap<>();
        m.put("Cardiology", List.of("heart", "chest pain", "palpitations", "breathless", "cardio", "blood pressure"));
        m.put("Neurology", List.of("headache", "migraine", "seizure", "dizziness", "blurred vision", "numbness", "neuro"));
        m.put("Orthopedics", List.of("knee", "bone", "fracture", "joint", "back pain", "sprain", "ortho"));
        m.put("Dermatology", List.of("rash", "skin", "acne", "itch", "eczema", "derma"));
        m.put("Pediatrics", List.of("child", "baby", "infant", "toddler"));
        m.put("ENT", List.of("ear", "throat", "sinus", "nose", "hearing", "tonsil"));
        m.put("Ophthalmology", List.of("eye", "vision", "blurred vision", "sight"));
        m.put("General Medicine", List.of("fever", "cold", "flu", "cough", "fatigue", "infection", "vomiting"));
        SYMPTOM_DEPARTMENT_KEYWORDS = Collections.unmodifiableMap(m);
    }

    private final DoctorServiceClient doctorServiceClient;
    private final QueueEntryRepository queueEntryRepository;
    private final AiTriageService aiTriageService;

    /** Upper bound on how many doctors to load (same convention as the Admin overview). */
    private final int maxDoctorsToLoad;

    public DoctorRecommendationService(DoctorServiceClient doctorServiceClient,
                                       QueueEntryRepository queueEntryRepository,
                                       AiTriageService aiTriageService,
                                       @Value("${queue.max-doctors-to-load:1000}") int maxDoctorsToLoad) {
        this.doctorServiceClient = doctorServiceClient;
        this.queueEntryRepository = queueEntryRepository;
        this.aiTriageService = aiTriageService;
        this.maxDoctorsToLoad = maxDoctorsToLoad;
    }

    @Transactional(readOnly = true)
    public DoctorSuggestionResponseDto recommend(String symptomText) {
        List<DoctorCatalogResponseDto> doctors = fetchAllDoctors();

        // Canonical department names the LLM may pick from (live from the catalog).
        List<String> departments = doctors.stream()
                .map(DoctorCatalogResponseDto::getDepartmentName)
                .filter(Objects::nonNull)
                .distinct()
                .collect(Collectors.toList());

        AiAssessment assessment = aiTriageService.assessWithFallback(symptomText, departments);
        String suggestedDepartment = normalizeDepartment(assessment.department(), departments);

        List<ScoredDoctor> candidates = scoreCandidates(doctors, suggestedDepartment, symptomText);

        // Best relevance first, then the shortest live wait, then name for stability.
        candidates.sort(Comparator
                .comparingInt(ScoredDoctor::score).reversed()
                .thenComparingInt(ScoredDoctor::predictedWaitMinutes)
                .thenComparing(s -> s.doctor().getName() == null ? "" : s.doctor().getName()));

        List<DoctorSuggestionDto> suggestions = candidates.stream()
                .limit(TOP_N)
                .map(this::toDto)
                .collect(Collectors.toList());

        DoctorSuggestionResponseDto response = new DoctorSuggestionResponseDto();
        response.setTriageLevel(assessment.triage());
        response.setSuggestedDepartment(suggestedDepartment);
        response.setEmergency(assessment.triage() == TriageLevel.EMERGENCY);
        response.setUrgencyNote(response.isEmergency()
                ? "EMERGENCY — please seek immediate attention. Nearest available specialists:"
                : null);
        response.setSuggestions(suggestions);
        return response;
    }

    // ─────────────────────────────────────────────────────────────
    // Scoring
    // ─────────────────────────────────────────────────────────────

    private List<ScoredDoctor> scoreCandidates(List<DoctorCatalogResponseDto> doctors,
                                               String suggestedDepartment,
                                               String symptomText) {
        // Load all active entries ONCE and group by doctor (mirrors the Admin
        // live overview) — avoids one DB query per doctor on a patient-facing
        // endpoint that can be called frequently.
        Map<Long, List<QueueEntry>> activeByDoctor = queueEntryRepository
                .findAllByStatusIn(QueueOrderingService.ACTIVE_STATUSES)
                .stream()
                .collect(Collectors.groupingBy(QueueEntry::getDoctorCatalogEntryId));

        List<ScoredDoctor> candidates = new ArrayList<>();
        for (DoctorCatalogResponseDto doctor : doctors) {
            // Never recommend an unavailable doctor.
            if (!Boolean.TRUE.equals(doctor.getIsAvailable())) {
                continue;
            }
            int score = matchScore(doctor, suggestedDepartment, symptomText);
            if (score == 0) {
                continue;
            }
            candidates.add(scoreDoctor(doctor, score, activeByDoctor));
        }

        // No specialty matched anything: fall back to the nearest available
        // doctors so the patient still gets a useful, honest suggestion.
        if (candidates.isEmpty()) {
            for (DoctorCatalogResponseDto doctor : doctors) {
                if (Boolean.TRUE.equals(doctor.getIsAvailable())) {
                    candidates.add(scoreDoctor(doctor, 1, activeByDoctor));
                }
            }
        }
        return candidates;
    }

    /** Live queue load: position + predicted wait a NEW joiner would face. */
    private ScoredDoctor scoreDoctor(DoctorCatalogResponseDto doctor, int score,
                                     Map<Long, List<QueueEntry>> activeByDoctor) {
        int activeCount = activeByDoctor.getOrDefault(doctor.getId(), List.of()).size();
        int avg = doctor.getAvgConsultationTimeMinutes() != null
                ? doctor.getAvgConsultationTimeMinutes() : 0;
        return new ScoredDoctor(doctor, score, activeCount + 1, activeCount * avg,
                score == 1 ? "No exact specialty match — nearest available doctor"
                        : buildReason(doctor, score));
    }

    /**
     * Relevance score 1-100. Priority:
     *   100 — exact department match on the LLM's pick
     *    80 — the LLM's pick appears in the doctor's specialization
     *    60 — symptom keyword found in the department name (LLM fallback)
     *    50 — symptom keyword found in the specialization (LLM fallback)
     *     1 — only used in the last-resort "no match" path
     */
    private int matchScore(DoctorCatalogResponseDto doctor, String suggestedDepartment, String symptomText) {
        String dept = doctor.getDepartmentName();
        String spec = doctor.getSpecialization();

        if (suggestedDepartment != null) {
            if (dept != null && dept.equalsIgnoreCase(suggestedDepartment)) {
                return 100;
            }
            if (spec != null && containsIgnoreCase(spec, suggestedDepartment)) {
                return 80;
            }
            return 0;
        }

        // Keyword fallback (LLM unavailable): first the curated keyword map,
        // then literal department/specialty names inside the symptom text.
        // Both use WHOLE-WORD matching — a naive substring check makes "ent"
        // match inside "dentist" or "ear" inside "heart", mis-routing patients.
        String lower = symptomText.toLowerCase(Locale.ROOT);
        String keywordDepartment = keywordDepartment(lower);
        if (keywordDepartment != null && dept != null && dept.equalsIgnoreCase(keywordDepartment)) {
            return 60;
        }
        if (keywordDepartment != null && spec != null && containsIgnoreCase(spec, keywordDepartment)) {
            return 55;
        }
        if (dept != null && containsWholeWord(lower, dept.toLowerCase(Locale.ROOT))) {
            return 60;
        }
        if (spec != null && containsWholeWord(lower, spec.toLowerCase(Locale.ROOT))) {
            return 50;
        }
        return 0;
    }

    /**
     * First department whose symptom keyword appears as a WHOLE WORD in the
     * lowercased text, or null. Whole-word matching is deliberate: a naive
     * substring check makes "ear" match inside "heart" (h-e-a-r-t), sending
     * chest-pain patients to ENT.
     */
    private String keywordDepartment(String lowerText) {
        for (Map.Entry<String, List<String>> e : SYMPTOM_DEPARTMENT_KEYWORDS.entrySet()) {
            for (String keyword : e.getValue()) {
                if (containsWholeWord(lowerText, keyword)) {
                    return e.getKey();
                }
            }
        }
        return null;
    }

    /** True when {@code keyword} appears in {@code text} not glued to other letters/digits. */
    private boolean containsWholeWord(String text, String keyword) {
        int idx = text.indexOf(keyword);
        if (idx < 0) {
            return false;
        }
        boolean beforeOk = idx == 0 || !Character.isLetterOrDigit(text.charAt(idx - 1));
        int after = idx + keyword.length();
        boolean afterOk = after >= text.length() || !Character.isLetterOrDigit(text.charAt(after));
        return beforeOk && afterOk;
    }

    private String buildReason(DoctorCatalogResponseDto doctor, int score) {
        if (score == 100) {
            return "Best match — " + doctor.getDepartmentName();
        }
        if (score == 80 || score == 55) {
            return "Specialist match — " + doctor.getSpecialization();
        }
        return "Specialist hint found in your description";
    }

    /**
     * Normalizes the LLM's free-text department answer to one of the real
     * department names (exact ignore-case first, then containment either way).
     * Returns null when nothing matches — the caller then uses keyword fallback.
     */
    private String normalizeDepartment(String raw, List<String> departments) {
        if (raw == null || raw.isBlank() || departments.isEmpty()) {
            return null;
        }
        String trimmed = raw.trim();
        for (String d : departments) {
            if (d.equalsIgnoreCase(trimmed)) {
                return d;
            }
        }
        for (String d : departments) {
            if (containsIgnoreCase(d, trimmed) || containsIgnoreCase(trimmed, d)) {
                return d;
            }
        }
        log.debug("LLM suggested department '{}' matches no catalog department — using keyword fallback", trimmed);
        return null;
    }

    private boolean containsIgnoreCase(String haystack, String needle) {
        return haystack.toLowerCase(Locale.ROOT).contains(needle.toLowerCase(Locale.ROOT));
    }

    private DoctorSuggestionDto toDto(ScoredDoctor s) {
        DoctorCatalogResponseDto d = s.doctor();
        DoctorSuggestionDto dto = new DoctorSuggestionDto();
        dto.setDoctorCatalogEntryId(d.getId());
        dto.setName(d.getName());
        dto.setDepartmentName(d.getDepartmentName());
        dto.setSpecialization(d.getSpecialization());
        dto.setQualification(d.getQualification());
        dto.setExperienceYears(d.getExperienceYears());
        dto.setConsultationFee(d.getConsultationFee());
        dto.setAvgConsultationTimeMinutes(d.getAvgConsultationTimeMinutes());
        dto.setIsAvailable(d.getIsAvailable());
        dto.setPosition(s.position());
        dto.setPredictedWaitMinutes(s.predictedWaitMinutes());
        dto.setMatchReason(s.reason());
        return dto;
    }

    private List<DoctorCatalogResponseDto> fetchAllDoctors() {
        try {
            return doctorServiceClient.getAllDoctors(0, maxDoctorsToLoad).getContent();
        } catch (RuntimeException e) {
            log.error("Failed to reach doctor-service while building doctor suggestions: {}", e.getMessage());
            throw new DoctorServiceUnavailableException(
                    "Doctor service is temporarily unavailable — please try again shortly");
        }
    }

    /** Internal holder pairing a catalog doctor with its computed match data. */
    private record ScoredDoctor(DoctorCatalogResponseDto doctor, int score,
                                int position, int predictedWaitMinutes, String reason) {
    }
}
