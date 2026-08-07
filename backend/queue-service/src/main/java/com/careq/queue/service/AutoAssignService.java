package com.careq.queue.service;

import com.careq.queue.dto.AutoAssignRequestDto;
import com.careq.queue.dto.AutoAssignResponseDto;
import com.careq.queue.dto.JoinQueueRequestDto;
import com.careq.queue.dto.QueueEntryResponseDto;
import com.careq.queue.entity.TriageLevel;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

/**
 * Phase 2 — full auto-assignment ("describe and done").
 *
 * Given free-text symptoms, the AI picks the SINGLE best available doctor and
 * joins that queue on the patient's behalf. The join is a normal queue entry —
 * same triage, position and predicted wait as a manual join, just with the
 * doctor chosen for the patient instead of by the patient.
 *
 * Decision rule (deliberately conservative):
 *  - AUTO-ASSIGN only when the best candidate is a confident match (score
 *    &ge; 60 — an LLM department hit or a curated keyword hit). Emergency
 *    symptoms are still auto-assigned — they need care fastest — and the
 *    urgency warning is carried in the response for the UI to show
 *    prominently.
 *  - AMBIGUOUS (no confident specialty signal) or NO_AVAILABLE_DOCTORS:
 *    nothing is joined; the top candidates are returned for the patient to
 *    confirm manually (the Phase 1 flow).
 *
 * The triage computed here is passed to
 * {@link QueueService#joinQueueWithAssessment} so the LLM is called once and
 * the persisted triage can never drift from what the patient was shown.
 */
@Service
public class AutoAssignService {

    /** Below this relevance the AI will not act on the patient's behalf. */
    private static final int AUTO_ASSIGN_MIN_SCORE = 60;

    /** How many candidates to offer when the symptoms are ambiguous. */
    private static final int TOP_N = 3;

    private final DoctorRecommendationService recommendationService;
    private final QueueService queueService;

    public AutoAssignService(DoctorRecommendationService recommendationService,
                             QueueService queueService) {
        this.recommendationService = recommendationService;
        this.queueService = queueService;
    }

    @Transactional
    public AutoAssignResponseDto autoAssign(String patientId, AutoAssignRequestDto request) {
        DoctorRecommendationService.RankedResult ranked =
                recommendationService.rank(request.getSymptomText());
        List<DoctorRecommendationService.ScoredDoctor> candidates = ranked.candidates();

        AutoAssignResponseDto response = new AutoAssignResponseDto();
        response.setTriageLevel(ranked.assessment().triage());
        response.setSuggestedDepartment(ranked.suggestedDepartment());
        response.setEmergency(ranked.assessment().triage() == TriageLevel.EMERGENCY);
        response.setUrgencyNote(response.isEmergency()
                ? "EMERGENCY — please seek immediate attention. Matching you with the nearest available specialist."
                : null);

        if (candidates.isEmpty()) {
            response.setAssigned(false);
            response.setReason("NO_AVAILABLE_DOCTORS");
            response.setMessage("No doctors are currently accepting new patients — please try again shortly.");
            response.setSuggestions(List.of());
            return response;
        }

        DoctorRecommendationService.ScoredDoctor best = candidates.get(0);
        if (best.score() < AUTO_ASSIGN_MIN_SCORE) {
            // The symptoms don't clearly point to one specialty — never guess
            // on the patient's behalf. Offer the candidates to confirm instead.
            response.setAssigned(false);
            response.setReason("AMBIGUOUS_SYMPTOMS");
            response.setMessage("Your symptoms don't clearly point to one specialty — please choose a doctor below.");
            response.setSuggestions(candidates.stream()
                    .limit(TOP_N)
                    .map(recommendationService::toDto)
                    .collect(Collectors.toList()));
            return response;
        }

        // Confident match → join the patient behind the best doctor.
        JoinQueueRequestDto joinRequest = new JoinQueueRequestDto();
        joinRequest.setDoctorCatalogEntryId(best.doctor().getId());
        joinRequest.setSymptomText(request.getSymptomText());
        joinRequest.setPatientName(request.getPatientName());
        QueueEntryResponseDto entry = queueService.joinQueueWithAssessment(
                patientId, joinRequest, ranked.assessment());

        response.setAssigned(true);
        response.setReason("ASSIGNED");
        response.setAssignedDoctor(recommendationService.toDto(best));
        response.setEntry(entry);
        response.setMessage("You've been matched with " + entry.getDoctorName()
                + " (" + entry.getDepartmentName() + ") — estimated wait ≈ "
                + entry.getPredictedWaitMinutes() + " min");
        return response;
    }
}
