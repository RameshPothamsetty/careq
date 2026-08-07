package com.careq.queue.service;

import com.careq.queue.dto.AiAssessment;
import com.careq.queue.dto.AutoAssignRequestDto;
import com.careq.queue.dto.AutoAssignResponseDto;
import com.careq.queue.dto.DoctorCatalogResponseDto;
import com.careq.queue.dto.DoctorSuggestionDto;
import com.careq.queue.dto.JoinQueueRequestDto;
import com.careq.queue.dto.QueueEntryResponseDto;
import com.careq.queue.entity.TriageLevel;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

/**
 * Unit tests for the Phase 2 auto-assign flow: the AI joins the single best
 * doctor automatically, and ambiguous symptoms fall back to suggestions
 * (never a guessed join on the patient's behalf).
 */
@ExtendWith(MockitoExtension.class)
class AutoAssignServiceTest {

    private static final String PATIENT = "patient-uuid";

    @Mock
    private DoctorRecommendationService recommendationService;
    @Mock
    private QueueService queueService;

    private AutoAssignService autoAssignService;

    private DoctorCatalogResponseDto doctor(Long id, String name, String department) {
        DoctorCatalogResponseDto d = new DoctorCatalogResponseDto();
        d.setId(id);
        d.setName(name);
        d.setDepartmentName(department);
        d.setSpecialization(department);
        d.setIsAvailable(true);
        return d;
    }

    private DoctorRecommendationService.ScoredDoctor scored(DoctorCatalogResponseDto doctor, int score) {
        return new DoctorRecommendationService.ScoredDoctor(doctor, score, 1, 0, "reason");
    }

    private AutoAssignRequestDto request(String symptomText) {
        AutoAssignRequestDto r = new AutoAssignRequestDto();
        r.setSymptomText(symptomText);
        r.setPatientName("Ada Patient");
        return r;
    }

    /** toDto is deterministic mapping logic — stub it so the decision flow can be asserted. */
    private void stubToDto() {
        given(recommendationService.toDto(any())).willAnswer(inv -> {
            DoctorRecommendationService.ScoredDoctor s = inv.getArgument(0);
            DoctorSuggestionDto dto = new DoctorSuggestionDto();
            dto.setDoctorCatalogEntryId(s.doctor().getId());
            dto.setName(s.doctor().getName());
            dto.setPosition(s.position());
            dto.setPredictedWaitMinutes(s.predictedWaitMinutes());
            return dto;
        });
    }

    @BeforeEach
    void setUp() {
        autoAssignService = new AutoAssignService(recommendationService, queueService);
    }

    @Test
    void autoAssign_ConfidentMatch_JoinsBehindBestDoctorWithPrecomputedTriage() {
        stubToDto();
        DoctorCatalogResponseDto cardio = doctor(10L, "Dr. Cardio", "Cardiology");
        given(recommendationService.rank("chest pain"))
                .willReturn(new DoctorRecommendationService.RankedResult(
                        new AiAssessment(TriageLevel.HIGH, "Cardiology"), "Cardiology",
                        List.of(scored(cardio, 100))));

        QueueEntryResponseDto entry = new QueueEntryResponseDto();
        entry.setId(1L);
        entry.setDoctorName("Dr. Cardio");
        entry.setDepartmentName("Cardiology");
        entry.setAiSuggestedTriage(TriageLevel.HIGH);
        entry.setPosition(1);
        entry.setPredictedWaitMinutes(0);
        given(queueService.joinQueueWithAssessment(eq(PATIENT), any(JoinQueueRequestDto.class),
                eq(new AiAssessment(TriageLevel.HIGH, "Cardiology")))).willReturn(entry);

        AutoAssignResponseDto response = autoAssignService.autoAssign(PATIENT, request("chest pain"));

        assertThat(response.isAssigned()).isTrue();
        assertThat(response.getReason()).isEqualTo("ASSIGNED");
        assertThat(response.getEntry()).isEqualTo(entry);
        assertThat(response.getAssignedDoctor().getDoctorCatalogEntryId()).isEqualTo(10L);
        assertThat(response.getTriageLevel()).isEqualTo(TriageLevel.HIGH);
        assertThat(response.getSuggestedDepartment()).isEqualTo("Cardiology");
        assertThat(response.getMessage()).contains("Dr. Cardio");
        // The join reuses the assessment computed for the decision (single LLM call).
        verify(queueService).joinQueueWithAssessment(eq(PATIENT),
                any(JoinQueueRequestDto.class), eq(new AiAssessment(TriageLevel.HIGH, "Cardiology")));
    }

    @Test
    void autoAssign_AmbiguousMatch_ReturnsSuggestionsAndNeverJoins() {
        stubToDto();
        DoctorCatalogResponseDto derma = doctor(10L, "Dr. Derma", "Dermatology");
        // Last-resort scoring (score 1) means no confident specialty signal.
        given(recommendationService.rank("vague complaints"))
                .willReturn(new DoctorRecommendationService.RankedResult(
                        new AiAssessment(TriageLevel.NORMAL, null), null,
                        List.of(scored(derma, 1))));

        AutoAssignResponseDto response = autoAssignService.autoAssign(PATIENT, request("vague complaints"));

        assertThat(response.isAssigned()).isFalse();
        assertThat(response.getReason()).isEqualTo("AMBIGUOUS_SYMPTOMS");
        assertThat(response.getSuggestions()).hasSize(1);
        assertThat(response.getSuggestions().get(0).getDoctorCatalogEntryId()).isEqualTo(10L);
        assertThat(response.getEntry()).isNull();
        assertThat(response.getAssignedDoctor()).isNull();
        // The patient must confirm — the AI never guesses on their behalf.
        verify(queueService, never()).joinQueueWithAssessment(anyString(), any(), any());
    }

    @Test
    void autoAssign_NoAvailableDoctors_ReturnsNoDoctorsOutcome() {
        given(recommendationService.rank("cough"))
                .willReturn(new DoctorRecommendationService.RankedResult(
                        new AiAssessment(TriageLevel.NORMAL, null), null, List.of()));

        AutoAssignResponseDto response = autoAssignService.autoAssign(PATIENT, request("cough"));

        assertThat(response.isAssigned()).isFalse();
        assertThat(response.getReason()).isEqualTo("NO_AVAILABLE_DOCTORS");
        assertThat(response.getSuggestions()).isEmpty();
        verify(queueService, never()).joinQueueWithAssessment(anyString(), any(), any());
    }

    @Test
    void autoAssign_Emergency_StillAutoAssignsWithUrgencyWarning() {
        DoctorCatalogResponseDto cardio = doctor(10L, "Dr. Cardio", "Cardiology");
        given(recommendationService.rank("severe chest pain"))
                .willReturn(new DoctorRecommendationService.RankedResult(
                        new AiAssessment(TriageLevel.EMERGENCY, "Cardiology"), "Cardiology",
                        List.of(scored(cardio, 100))));
        given(queueService.joinQueueWithAssessment(eq(PATIENT), any(JoinQueueRequestDto.class),
                any(AiAssessment.class))).willReturn(new QueueEntryResponseDto());

        AutoAssignResponseDto response = autoAssignService.autoAssign(PATIENT, request("severe chest pain"));

        // Emergencies are assigned fastest — with the warning carried for the UI.
        assertThat(response.isAssigned()).isTrue();
        assertThat(response.isEmergency()).isTrue();
        assertThat(response.getUrgencyNote()).isNotNull();
    }
}
