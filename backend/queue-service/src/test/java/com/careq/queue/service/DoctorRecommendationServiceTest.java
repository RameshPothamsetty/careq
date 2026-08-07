package com.careq.queue.service;

import com.careq.queue.client.DoctorServiceClient;
import com.careq.queue.dto.AiAssessment;
import com.careq.queue.dto.DoctorCatalogPageDto;
import com.careq.queue.dto.DoctorCatalogResponseDto;
import com.careq.queue.dto.DoctorSuggestionDto;
import com.careq.queue.dto.DoctorSuggestionResponseDto;
import com.careq.queue.entity.TriageLevel;
import com.careq.queue.exception.DoctorServiceUnavailableException;
import com.careq.queue.repository.QueueEntryRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;

/**
 * Unit tests for the AI doctor recommendation engine (Phase 1 of the
 * patient-to-doctor gap). doctor-service is simulated by mocking the Feign
 * client; the AI assessment service is mocked so both the LLM path and the
 * keyword-fallback path are asserted directly.
 */
@ExtendWith(MockitoExtension.class)
class DoctorRecommendationServiceTest {

    @Mock
    private DoctorServiceClient doctorServiceClient;
    @Mock
    private QueueEntryRepository queueEntryRepository;
    @Mock
    private AiTriageService aiTriageService;

    private DoctorRecommendationService recommendationService;

    private DoctorCatalogResponseDto doctor(Long id, String name, String department, String specialization,
                                            boolean available) {
        DoctorCatalogResponseDto d = new DoctorCatalogResponseDto();
        d.setId(id);
        d.setName(name);
        d.setDepartmentName(department);
        d.setSpecialization(specialization);
        d.setQualification("MBBS");
        d.setExperienceYears(10);
        d.setConsultationFee(new BigDecimal("300"));
        d.setAvgConsultationTimeMinutes(15);
        d.setIsAvailable(available);
        return d;
    }

    private void stubCatalog(DoctorCatalogResponseDto... doctors) {
        DoctorCatalogPageDto page = new DoctorCatalogPageDto();
        page.setContent(List.of(doctors));
        given(doctorServiceClient.getAllDoctors(0, 1000)).willReturn(page);
    }

    @BeforeEach
    void setUp() {
        recommendationService = new DoctorRecommendationService(
                doctorServiceClient, queueEntryRepository, aiTriageService, 1000);
    }

    @Test
    void recommend_ExactDepartmentMatch_RanksItFirstAndSkipsUnavailable() {
        DoctorCatalogResponseDto cardio = doctor(1L, "Dr. Cardio", "Cardiology", "Interventional Cardiology", true);
        DoctorCatalogResponseDto cardioOffline = doctor(2L, "Dr. Cardio 2", "Cardiology", "Cardiology", false);
        DoctorCatalogResponseDto neuro = doctor(3L, "Dr. Neuro", "Neurology", "Neurology", true);
        stubCatalog(cardio, cardioOffline, neuro);

        given(aiTriageService.assessWithFallback("chest pain", List.of("Cardiology", "Neurology")))
                .willReturn(new AiAssessment(TriageLevel.EMERGENCY, "Cardiology"));
        // No active queue entries. Only the matching available cardiologist is
        // evaluated — the offline one and the unrelated neurologist are filtered.
        given(queueEntryRepository.findAllByStatusIn(anyList())).willReturn(List.of());

        DoctorSuggestionResponseDto response =
                recommendationService.recommend("chest pain");

        // The offline cardiologist is never recommended, and with a confident
        // LLM department pick only the relevant specialist is shown.
        assertThat(response.getSuggestions()).extracting(DoctorSuggestionDto::getName)
                .containsExactly("Dr. Cardio");
        assertThat(response.getSuggestions().get(0).getMatchReason()).contains("Best match");
        assertThat(response.getTriageLevel()).isEqualTo(TriageLevel.EMERGENCY);
        assertThat(response.isEmergency()).isTrue();
        assertThat(response.getSuggestedDepartment()).isEqualTo("Cardiology");
    }

    @Test
    void recommend_ShortestWaitBreaksTies_AmongSameDepartment() {
        DoctorCatalogResponseDto busy = doctor(1L, "Dr. Busy", "Cardiology", "Cardiology", true);
        DoctorCatalogResponseDto free = doctor(2L, "Dr. Free", "Cardiology", "Cardiology", true);
        stubCatalog(busy, free);

        given(aiTriageService.assessWithFallback("palpitations", List.of("Cardiology")))
                .willReturn(new AiAssessment(TriageLevel.HIGH, "Cardiology"));
        // Dr. Busy (id 1) has 2 active patients (wait 2x15=30), Dr. Free (id 2)
        // has 0 (wait 0). All active entries are loaded in ONE grouped query.
        com.careq.queue.entity.QueueEntry busy1 = new com.careq.queue.entity.QueueEntry();
        busy1.setDoctorCatalogEntryId(1L);
        com.careq.queue.entity.QueueEntry busy2 = new com.careq.queue.entity.QueueEntry();
        busy2.setDoctorCatalogEntryId(1L);
        given(queueEntryRepository.findAllByStatusIn(anyList())).willReturn(List.of(busy1, busy2));

        DoctorSuggestionResponseDto response = recommendationService.recommend("palpitations");

        assertThat(response.getSuggestions()).extracting(DoctorSuggestionDto::getName)
                .containsExactly("Dr. Free", "Dr. Busy");
        assertThat(response.getSuggestions().get(0).getPredictedWaitMinutes()).isZero();
        assertThat(response.getSuggestions().get(1).getPredictedWaitMinutes()).isEqualTo(30);
        assertThat(response.getSuggestions().get(1).getPosition()).isEqualTo(3);
    }

    @Test
    void recommend_NoLlmDepartment_FallsBackToSymptomKeywordMatch() {
        DoctorCatalogResponseDto cardio = doctor(1L, "Dr. Cardio", "Cardiology", "Cardiology", true);
        DoctorCatalogResponseDto derma = doctor(2L, "Dr. Derma", "Dermatology", "Dermatology", true);
        stubCatalog(cardio, derma);

        // LLM down (fallback path): NORMAL triage, null department. The curated
        // symptom-keyword map ("heart" → Cardiology) still routes the patient
        // to the right specialist without the model.
        given(aiTriageService.assessWithFallback("heart palpitations", List.of("Cardiology", "Dermatology")))
                .willReturn(new AiAssessment(TriageLevel.NORMAL, null));
        // Keyword map routes to Cardiology only — Dermatology is never evaluated.
        given(queueEntryRepository.findAllByStatusIn(anyList())).willReturn(List.of());

        DoctorSuggestionResponseDto response =
                recommendationService.recommend("heart palpitations");

        // "heart" hints Cardiology via the symptom text, not Dermatology.
        assertThat(response.getSuggestedDepartment()).isNull();
        assertThat(response.getSuggestions()).extracting(DoctorSuggestionDto::getName)
                .containsExactly("Dr. Cardio");
    }

    @Test
    void recommend_NoSpecialtyMatch_ReturnsNearestAvailableDoctor() {
        DoctorCatalogResponseDto derma = doctor(1L, "Dr. Derma", "Dermatology", "Dermatology", true);
        DoctorCatalogResponseDto ortho = doctor(2L, "Dr. Ortho", "Orthopedics", "Orthopedics", true);
        stubCatalog(derma, ortho);

        given(aiTriageService.assessWithFallback("unrelated vague symptom", List.of("Dermatology", "Orthopedics")))
                .willReturn(new AiAssessment(TriageLevel.NORMAL, "Cardiology")); // no such department
        given(queueEntryRepository.findAllByStatusIn(anyList())).willReturn(List.of());

        DoctorSuggestionResponseDto response =
                recommendationService.recommend("unrelated vague symptom");

        // No exact match → last-resort suggestions, still useful and never empty.
        assertThat(response.getSuggestions()).hasSize(2);
        assertThat(response.getSuggestions().get(0).getMatchReason()).contains("nearest available");
    }

    @Test
    void recommend_NoAvailableDoctors_ReturnsEmptySuggestions() {
        DoctorCatalogResponseDto cardioOffline = doctor(1L, "Dr. Cardio", "Cardiology", "Cardiology", false);
        stubCatalog(cardioOffline);

        given(aiTriageService.assessWithFallback("chest pain", List.of("Cardiology")))
                .willReturn(new AiAssessment(TriageLevel.EMERGENCY, "Cardiology"));

        DoctorSuggestionResponseDto response = recommendationService.recommend("chest pain");

        assertThat(response.getSuggestions()).isEmpty();
        assertThat(response.getTriageLevel()).isEqualTo(TriageLevel.EMERGENCY);
        assertThat(response.isEmergency()).isTrue();
    }

    @Test
    void recommend_DoctorServiceDown_ThrowsGraceful503() {
        given(doctorServiceClient.getAllDoctors(0, 1000))
                .willThrow(new RuntimeException("connection refused"));

        assertThatThrownBy(() -> recommendationService.recommend("fever"))
                .isInstanceOf(DoctorServiceUnavailableException.class);
    }

    @Test
    void rank_ExposesFullSortedList_SingleBestIsLowestWait() {
        // Four cardiologists: Dr. One is busy (2 active patients), the rest free.
        // rank() must expose the WHOLE sorted list (not just the top 3) so the
        // auto-assign flow can take the single best = lowest predicted wait.
        DoctorCatalogResponseDto one = doctor(1L, "Dr. One", "Cardiology", "Cardiology", true);
        DoctorCatalogResponseDto two = doctor(2L, "Dr. Two", "Cardiology", "Cardiology", true);
        DoctorCatalogResponseDto three = doctor(3L, "Dr. Three", "Cardiology", "Cardiology", true);
        DoctorCatalogResponseDto four = doctor(4L, "Dr. Four", "Cardiology", "Cardiology", true);
        stubCatalog(one, two, three, four);

        given(aiTriageService.assessWithFallback("chest pain", List.of("Cardiology")))
                .willReturn(new AiAssessment(TriageLevel.HIGH, "Cardiology"));
        com.careq.queue.entity.QueueEntry busy1 = new com.careq.queue.entity.QueueEntry();
        busy1.setDoctorCatalogEntryId(1L);
        com.careq.queue.entity.QueueEntry busy2 = new com.careq.queue.entity.QueueEntry();
        busy2.setDoctorCatalogEntryId(1L);
        given(queueEntryRepository.findAllByStatusIn(anyList())).willReturn(List.of(busy1, busy2));

        DoctorRecommendationService.RankedResult ranked = recommendationService.rank("chest pain");

        // All four evaluated, best first: the free cardiologists (0 wait,
        // alphabetical name tiebreak) then the busy one (30 min) last — so the
        // single best (candidate 0) is never the slowest queue.
        assertThat(ranked.candidates()).hasSize(4);
        assertThat(ranked.candidates()).allSatisfy(c -> assertThat(c.score()).isEqualTo(100));
        assertThat(ranked.candidates().get(0).doctor().getName()).isEqualTo("Dr. Four");
        assertThat(ranked.candidates().get(0).predictedWaitMinutes()).isZero();
        assertThat(ranked.candidates().get(3).doctor().getName()).isEqualTo("Dr. One");
        assertThat(ranked.candidates().get(3).predictedWaitMinutes()).isEqualTo(30);
        assertThat(ranked.assessment().triage()).isEqualTo(TriageLevel.HIGH);
        assertThat(ranked.suggestedDepartment()).isEqualTo("Cardiology");
    }
}
