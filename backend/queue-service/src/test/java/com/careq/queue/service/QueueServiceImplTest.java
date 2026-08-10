package com.careq.queue.service;

import com.careq.queue.client.DoctorServiceClient;
import com.careq.queue.dto.AiAssessment;
import com.careq.queue.dto.AnalyticsSummaryDto;
import com.careq.queue.dto.DailyAvgWaitDto;
import com.careq.queue.dto.DailyPatientCountDto;
import com.careq.queue.dto.DepartmentDistributionDto;
import com.careq.queue.dto.DoctorAnalyticsSummaryDto;
import com.careq.queue.dto.DoctorCatalogResponseDto;
import com.careq.queue.dto.DoctorCatalogPageDto;
import com.careq.queue.dto.JoinQueueRequestDto;
import com.careq.queue.dto.OverrideTriageRequestDto;
import com.careq.queue.dto.QueueEntryResponseDto;
import com.careq.queue.entity.QueueEntry;
import com.careq.queue.entity.QueueStatus;
import com.careq.queue.entity.TriageLevel;
import com.careq.queue.exception.DoctorUnavailableException;
import com.careq.queue.exception.DuplicateQueueEntryException;
import com.careq.queue.exception.InvalidQueueStateException;
import com.careq.queue.exception.UnauthorizedAccessException;
import com.careq.queue.repository.DayAvgWaitProjection;
import com.careq.queue.repository.DayCountProjection;
import com.careq.queue.repository.DoctorCountProjection;
import com.careq.queue.repository.QueueEntryRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Pageable;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
/**
 * Service-layer tests for the queue module (Day 5).
 * doctor-service is simulated by mocking the Feign client; the AI triage
 * service is also mocked so failure paths can be asserted directly.
 */
@ExtendWith(MockitoExtension.class)
class QueueServiceImplTest {

    private static final String PATIENT = "patient-uuid";
    private static final String DOCTOR_USER = "doctor-uuid";
    private static final String ADMIN = "admin-uuid";

    @Mock
    private QueueEntryRepository queueEntryRepository;
    @Mock
    private DoctorServiceClient doctorServiceClient;
    @Mock
    private AiTriageService aiTriageService;
    @Mock
    private QueueEventPublisher eventPublisher;

    private final QueueOrderingService orderingService = new QueueOrderingService();

    private QueueServiceImpl queueService;

    private DoctorCatalogResponseDto availableDoctor() {
        DoctorCatalogResponseDto d = new DoctorCatalogResponseDto();
        d.setId(10L);
        d.setUserId(DOCTOR_USER);
        d.setName("Dr. Arjun Sharma");
        d.setSpecialization("Cardiology");
        d.setDepartmentName("Cardiology");
        d.setAvgConsultationTimeMinutes(15);
        d.setIsAvailable(true);
        return d;
    }

    @BeforeEach
    void setUp() {
        queueService = new QueueServiceImpl(
                queueEntryRepository, doctorServiceClient, aiTriageService, orderingService,
                eventPublisher, 30, 1000);
    }

    @Test
    void joinQueue_Success_ReturnsEntryWithPositionAndPredictedWait() {
        given(doctorServiceClient.getDoctorById(10L)).willReturn(availableDoctor());
        given(queueEntryRepository.existsByPatientIdAndDoctorCatalogEntryIdAndStatusIn(
                eq(PATIENT), eq(10L), anyList())).willReturn(false);
        given(aiTriageService.classifyWithFallback("severe chest pain")).willReturn(TriageLevel.EMERGENCY);

        QueueEntry saved = new QueueEntry();
        saved.setId(1L);
        saved.setPatientId(PATIENT);
        saved.setDoctorCatalogEntryId(10L);
        saved.setSymptomText("severe chest pain");
        saved.setAiSuggestedTriage(TriageLevel.EMERGENCY);
        saved.setStatus(QueueStatus.WAITING);
        saved.setJoinedAt(LocalDateTime.of(2026, 7, 31, 9, 0));
        given(queueEntryRepository.save(any(QueueEntry.class))).willReturn(saved);
        given(queueEntryRepository.findByDoctorCatalogEntryIdAndStatusInOrderByJoinedAtAsc(eq(10L), anyList()))
                .willReturn(List.of(saved));

        JoinQueueRequestDto request = new JoinQueueRequestDto();
        request.setDoctorCatalogEntryId(10L);
        request.setSymptomText("severe chest pain");

        QueueEntryResponseDto response = queueService.joinQueue(PATIENT, request);

        assertThat(response.getAiSuggestedTriage()).isEqualTo(TriageLevel.EMERGENCY);
        assertThat(response.getPosition()).isEqualTo(1);
        assertThat(response.getPredictedWaitMinutes()).isZero();
        assertThat(response.getEffectiveTriage()).isEqualTo(TriageLevel.EMERGENCY);
    }

    @Test
    void joinQueue_PublishesQueueJoinedEvent() {
        given(doctorServiceClient.getDoctorById(10L)).willReturn(availableDoctor());
        given(queueEntryRepository.existsByPatientIdAndDoctorCatalogEntryIdAndStatusIn(
                eq(PATIENT), eq(10L), anyList())).willReturn(false);
        given(aiTriageService.classifyWithFallback("severe chest pain")).willReturn(TriageLevel.EMERGENCY);

        QueueEntry saved = new QueueEntry();
        saved.setId(1L);
        saved.setPatientId(PATIENT);
        saved.setPatientName("John Patient");
        saved.setDoctorCatalogEntryId(10L);
        saved.setSymptomText("severe chest pain");
        saved.setAiSuggestedTriage(TriageLevel.EMERGENCY);
        saved.setStatus(QueueStatus.WAITING);
        saved.setJoinedAt(LocalDateTime.of(2026, 7, 31, 9, 0));
        given(queueEntryRepository.save(any(QueueEntry.class))).willReturn(saved);
        given(queueEntryRepository.findByDoctorCatalogEntryIdAndStatusInOrderByJoinedAtAsc(eq(10L), anyList()))
                .willReturn(List.of(saved));

        JoinQueueRequestDto request = new JoinQueueRequestDto();
        request.setDoctorCatalogEntryId(10L);
        request.setSymptomText("severe chest pain");

        queueService.joinQueue(PATIENT, request);

        // The join publishes a queue.joined event carrying the patient as recipient.
        verify(eventPublisher).publish(eq(QueueEventPublisher.QUEUE_JOINED), argThat(event ->
                PATIENT.equals(event.getRecipientUserId())
                        && "Dr. Arjun Sharma".equals(event.getDoctorName())
                        && event.getPosition() == 1
                        && "EMERGENCY".equals(event.getTriageLevel())));
    }

    @Test
    void joinQueue_UnavailableDoctor_Throws() {
        DoctorCatalogResponseDto doctor = availableDoctor();
        doctor.setIsAvailable(false);
        given(doctorServiceClient.getDoctorById(10L)).willReturn(doctor);

        JoinQueueRequestDto request = new JoinQueueRequestDto();
        request.setDoctorCatalogEntryId(10L);
        request.setSymptomText("cough");

        assertThatThrownBy(() -> queueService.joinQueue(PATIENT, request))
                .isInstanceOf(DoctorUnavailableException.class);
        verify(queueEntryRepository, never()).save(any(QueueEntry.class));
    }

    @Test
    void joinQueue_DuplicateActiveEntry_Throws() {
        given(doctorServiceClient.getDoctorById(10L)).willReturn(availableDoctor());
        given(queueEntryRepository.existsByPatientIdAndDoctorCatalogEntryIdAndStatusIn(
                eq(PATIENT), eq(10L), anyList())).willReturn(true);

        JoinQueueRequestDto request = new JoinQueueRequestDto();
        request.setDoctorCatalogEntryId(10L);
        request.setSymptomText("fever");

        assertThatThrownBy(() -> queueService.joinQueue(PATIENT, request))
                .isInstanceOf(DuplicateQueueEntryException.class);
    }

    @Test
    void joinQueueWithAssessment_UsesPrecomputedTriage_WithoutSecondAiCall() {
        given(doctorServiceClient.getDoctorById(10L)).willReturn(availableDoctor());
        given(queueEntryRepository.existsByPatientIdAndDoctorCatalogEntryIdAndStatusIn(
                eq(PATIENT), eq(10L), anyList())).willReturn(false);
        // No aiTriageService stub — proves the precomputed assessment is used.

        QueueEntry saved = new QueueEntry();
        saved.setId(1L);
        saved.setPatientId(PATIENT);
        saved.setDoctorCatalogEntryId(10L);
        saved.setSymptomText("severe chest pain");
        saved.setAiSuggestedTriage(TriageLevel.EMERGENCY);
        saved.setStatus(QueueStatus.WAITING);
        saved.setJoinedAt(LocalDateTime.of(2026, 8, 1, 9, 0));
        given(queueEntryRepository.save(any(QueueEntry.class))).willReturn(saved);
        given(queueEntryRepository.findByDoctorCatalogEntryIdAndStatusInOrderByJoinedAtAsc(eq(10L), anyList()))
                .willReturn(List.of(saved));

        JoinQueueRequestDto request = new JoinQueueRequestDto();
        request.setDoctorCatalogEntryId(10L);
        request.setSymptomText("severe chest pain");

        QueueEntryResponseDto response = queueService.joinQueueWithAssessment(
                PATIENT, request, new AiAssessment(TriageLevel.EMERGENCY, "Cardiology"));

        assertThat(response.getAiSuggestedTriage()).isEqualTo(TriageLevel.EMERGENCY);
        assertThat(response.getPosition()).isEqualTo(1);
        // The AI wrapper is never consulted again on this path — single LLM call.
        verify(aiTriageService, never()).classifyWithFallback(any());
    }

    @Test
    void overrideTriage_Success_ChangesEffectiveTriage() {
        QueueEntry entry = new QueueEntry();
        entry.setId(1L);
        entry.setPatientId(PATIENT);
        entry.setDoctorCatalogEntryId(10L);
        entry.setSymptomText("mild pain");
        entry.setAiSuggestedTriage(TriageLevel.NORMAL);
        entry.setStatus(QueueStatus.WAITING);
        entry.setJoinedAt(LocalDateTime.of(2026, 7, 31, 9, 0));

        given(queueEntryRepository.findById(1L)).willReturn(Optional.of(entry));
        given(doctorServiceClient.getDoctorById(10L)).willReturn(availableDoctor());
        given(queueEntryRepository.save(any(QueueEntry.class))).willAnswer(inv -> inv.getArgument(0));
        given(queueEntryRepository.findByDoctorCatalogEntryIdAndStatusInOrderByJoinedAtAsc(eq(10L), anyList()))
                .willReturn(List.of(entry));

        OverrideTriageRequestDto request = new OverrideTriageRequestDto();
        request.setTriageLevel(TriageLevel.EMERGENCY);

        QueueEntryResponseDto response =
                queueService.overrideTriage(1L, request, DOCTOR_USER, "DOCTOR");

        assertThat(response.getDoctorOverrideTriage()).isEqualTo(TriageLevel.EMERGENCY);
        assertThat(response.getEffectiveTriage()).isEqualTo(TriageLevel.EMERGENCY);
        assertThat(response.getPosition()).isEqualTo(1);
    }

    @Test
    void overrideTriage_CompletedEntry_Throws() {
        QueueEntry entry = new QueueEntry();
        entry.setId(1L);
        entry.setPatientId(PATIENT);
        entry.setDoctorCatalogEntryId(10L);
        entry.setSymptomText("done");
        entry.setAiSuggestedTriage(TriageLevel.NORMAL);
        entry.setStatus(QueueStatus.COMPLETED);
        entry.setCompletedAt(LocalDateTime.of(2026, 7, 31, 10, 0));

        given(queueEntryRepository.findById(1L)).willReturn(Optional.of(entry));
        given(doctorServiceClient.getDoctorById(10L)).willReturn(availableDoctor());

        OverrideTriageRequestDto request = new OverrideTriageRequestDto();
        request.setTriageLevel(TriageLevel.HIGH);

        assertThatThrownBy(() -> queueService.overrideTriage(1L, request, DOCTOR_USER, "DOCTOR"))
                .isInstanceOf(InvalidQueueStateException.class);
    }

    @Test
    void overrideTriage_AnotherDoctor_Throws() {
        QueueEntry entry = new QueueEntry();
        entry.setId(1L);
        entry.setPatientId(PATIENT);
        entry.setDoctorCatalogEntryId(10L);
        entry.setSymptomText("cough");
        entry.setAiSuggestedTriage(TriageLevel.NORMAL);
        entry.setStatus(QueueStatus.WAITING);

        given(queueEntryRepository.findById(1L)).willReturn(Optional.of(entry));
        given(doctorServiceClient.getDoctorById(10L)).willReturn(availableDoctor());

        OverrideTriageRequestDto request = new OverrideTriageRequestDto();
        request.setTriageLevel(TriageLevel.HIGH);

        // A different doctor (not DOCTOR_USER) tries to override.
        assertThatThrownBy(() -> queueService.overrideTriage(1L, request, "someone-else", "DOCTOR"))
                .isInstanceOf(UnauthorizedAccessException.class);
    }

    @Test
    void getDoctorQueue_WithPatientNameSearch_FiltersRows() {
        QueueEntry alice = new QueueEntry();
        alice.setId(1L);
        alice.setPatientId("p-alice");
        alice.setPatientName("Alice Wonder");
        alice.setDoctorCatalogEntryId(10L);
        alice.setSymptomText("fever");
        alice.setAiSuggestedTriage(TriageLevel.NORMAL);
        alice.setStatus(QueueStatus.WAITING);
        alice.setJoinedAt(LocalDateTime.of(2026, 8, 1, 9, 0));

        QueueEntry bob = new QueueEntry();
        bob.setId(2L);
        bob.setPatientId("p-bob");
        bob.setPatientName("Bob Smith");
        bob.setDoctorCatalogEntryId(10L);
        bob.setSymptomText("cough");
        bob.setAiSuggestedTriage(TriageLevel.NORMAL);
        bob.setStatus(QueueStatus.WAITING);
        bob.setJoinedAt(LocalDateTime.of(2026, 8, 1, 9, 5));

        given(doctorServiceClient.getDoctorById(10L)).willReturn(availableDoctor());
        given(queueEntryRepository.findByDoctorCatalogEntryIdAndStatusInOrderByJoinedAtAsc(eq(10L), anyList()))
                .willReturn(List.of(alice, bob));

        List<QueueEntryResponseDto> result =
                queueService.getDoctorQueue(10L, "alice", DOCTOR_USER, "DOCTOR");

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getPatientName()).isEqualTo("Alice Wonder");
        // The search is case-insensitive and only narrows rows.
        List<QueueEntryResponseDto> all =
                queueService.getDoctorQueue(10L, "", DOCTOR_USER, "DOCTOR");
        assertThat(all).hasSize(2);
    }

    @Test
    void callNext_ThenComplete_AdvancesAndFinishesEntry() {
        QueueEntry entry = new QueueEntry();
        entry.setId(1L);
        entry.setPatientId(PATIENT);
        entry.setDoctorCatalogEntryId(10L);
        entry.setSymptomText("chest pain");
        entry.setAiSuggestedTriage(TriageLevel.EMERGENCY);
        entry.setStatus(QueueStatus.WAITING);
        entry.setJoinedAt(LocalDateTime.of(2026, 7, 31, 9, 0));

        given(queueEntryRepository.findById(1L)).willReturn(Optional.of(entry));
        given(doctorServiceClient.getDoctorById(10L)).willReturn(availableDoctor());
        given(queueEntryRepository.save(any(QueueEntry.class))).willAnswer(inv -> inv.getArgument(0));
        given(queueEntryRepository.findByDoctorCatalogEntryIdAndStatusInOrderByJoinedAtAsc(eq(10L), anyList()))
                .willReturn(List.of(entry));

        QueueEntryResponseDto called = queueService.callNext(1L, DOCTOR_USER, "DOCTOR");
        assertThat(called.getStatus()).isEqualTo(QueueStatus.IN_PROGRESS);
        assertThat(called.getCalledAt()).isNotNull();

        QueueEntryResponseDto completed = queueService.complete(1L, DOCTOR_USER, "DOCTOR");
        assertThat(completed.getStatus()).isEqualTo(QueueStatus.COMPLETED);
        assertThat(completed.getCompletedAt()).isNotNull();
        // No longer in the active queue => no derived position.
        assertThat(completed.getPosition()).isNull();
    }

    @Test
    void complete_NotInProgress_Throws() {
        QueueEntry entry = new QueueEntry();
        entry.setId(1L);
        entry.setPatientId(PATIENT);
        entry.setDoctorCatalogEntryId(10L);
        entry.setSymptomText("cough");
        entry.setAiSuggestedTriage(TriageLevel.NORMAL);
        entry.setStatus(QueueStatus.WAITING);

        given(queueEntryRepository.findById(1L)).willReturn(Optional.of(entry));
        given(doctorServiceClient.getDoctorById(10L)).willReturn(availableDoctor());

        assertThatThrownBy(() -> queueService.complete(1L, DOCTOR_USER, "DOCTOR"))
                .isInstanceOf(InvalidQueueStateException.class);
    }

    // ── Analytics (Day 7b) ────────────────────────────────────────

    @Test
    void getAnalyticsSummary_EmptyData_ReturnsZeroFilledSevenDayWindow() {
        given(queueEntryRepository.countCompletedPerDaySince(any())).willReturn(List.of());
        given(queueEntryRepository.avgCalledWaitPerDaySince(any())).willReturn(List.of());
        DoctorCatalogPageDto emptyPage = new DoctorCatalogPageDto();
        emptyPage.setContent(List.of());
        given(doctorServiceClient.getAllDoctors(0, 1000)).willReturn(emptyPage);
        given(queueEntryRepository.countCompletedPerDoctorSince(any())).willReturn(List.of());

        AnalyticsSummaryDto summary = queueService.getAnalyticsSummary();

        // Seven days, all zero-filled, never crashing on an empty dataset.
        assertThat(summary.getPatientsPerDay()).hasSize(7);
        assertThat(summary.getPatientsPerDay()).allSatisfy(d -> assertThat(d.getCount()).isZero());
        assertThat(summary.getAvgWaitTimeTrend()).hasSize(7);
        assertThat(summary.getAvgWaitTimeTrend()).allSatisfy(d -> assertThat(d.getAvgWaitMinutes()).isNull());
        assertThat(summary.getDepartmentDistribution()).isEmpty();
        // Days are consecutive and ascending, ending today.
        List<DailyPatientCountDto> days = summary.getPatientsPerDay();
        assertThat(days.get(days.size() - 1).getDate()).isEqualTo(java.time.LocalDate.now());
        assertThat(days.get(1).getDate()).isEqualTo(days.get(0).getDate().plusDays(1));
    }

    @Test
    void getAnalyticsSummary_PopulatedData_AggregatesAndMapsDepartments() {
        java.time.LocalDate today = java.time.LocalDate.now();
        String todayStr = today.toString();
        String yesterdayStr = today.minusDays(1).toString();

        DayCountProjection todayCount = mock(DayCountProjection.class);
        given(todayCount.getDay()).willReturn(todayStr);
        given(todayCount.getCnt()).willReturn(3L);
        DayCountProjection yesterdayCount = mock(DayCountProjection.class);
        given(yesterdayCount.getDay()).willReturn(yesterdayStr);
        given(yesterdayCount.getCnt()).willReturn(2L);

        DayAvgWaitProjection todayWait = mock(DayAvgWaitProjection.class);
        given(todayWait.getDay()).willReturn(todayStr);
        given(todayWait.getAvgWait()).willReturn(12.5);
        DayAvgWaitProjection yesterdayWait = mock(DayAvgWaitProjection.class);
        given(yesterdayWait.getDay()).willReturn(yesterdayStr);
        given(yesterdayWait.getAvgWait()).willReturn(8.0);

        DoctorCountProjection cardio = mock(DoctorCountProjection.class);
        given(cardio.getDoctorCatalogEntryId()).willReturn(10L);
        given(cardio.getCnt()).willReturn(4L);
        DoctorCountProjection neuro = mock(DoctorCountProjection.class);
        given(neuro.getDoctorCatalogEntryId()).willReturn(20L);
        given(neuro.getCnt()).willReturn(1L);

        DoctorCatalogPageDto page = new DoctorCatalogPageDto();
        DoctorCatalogResponseDto cardioDoctor = availableDoctor(); // departmentName = "Cardiology"
        DoctorCatalogResponseDto neuroDoctor = availableDoctor();
        neuroDoctor.setId(20L);
        neuroDoctor.setDepartmentName("Neurology");
        page.setContent(List.of(cardioDoctor, neuroDoctor));

        given(queueEntryRepository.countCompletedPerDaySince(any())).willReturn(List.of(todayCount, yesterdayCount));
        given(queueEntryRepository.avgCalledWaitPerDaySince(any())).willReturn(List.of(todayWait, yesterdayWait));
        given(queueEntryRepository.countCompletedPerDoctorSince(any())).willReturn(List.of(cardio, neuro));
        given(doctorServiceClient.getAllDoctors(0, 1000)).willReturn(page);

        AnalyticsSummaryDto summary = queueService.getAnalyticsSummary();

        // Yesterday's 2 + today's 3 appear at the right slots; other days zero.
        List<DailyPatientCountDto> patientsPerDay = summary.getPatientsPerDay();
        assertThat(patientsPerDay).hasSize(7);
        assertThat(patientsPerDay.stream()
                .filter(d -> d.getDate().equals(today)).findFirst().orElseThrow().getCount()).isEqualTo(3L);
        assertThat(patientsPerDay.stream()
                .filter(d -> d.getDate().equals(today.minusDays(1))).findFirst().orElseThrow().getCount()).isEqualTo(2L);

        List<DailyAvgWaitDto> trend = summary.getAvgWaitTimeTrend();
        assertThat(trend.stream()
                .filter(d -> d.getDate().equals(today)).findFirst().orElseThrow().getAvgWaitMinutes()).isEqualTo(12.5);
        assertThat(trend.stream()
                .filter(d -> d.getDate().equals(today.minusDays(2))).findFirst().orElseThrow().getAvgWaitMinutes()).isNull();

        // Department distribution: 4 Cardiology, 1 Neurology, sorted descending.
        List<DepartmentDistributionDto> distribution = summary.getDepartmentDistribution();
        assertThat(distribution).hasSize(2);
        assertThat(distribution.get(0).getDepartmentName()).isEqualTo("Cardiology");
        assertThat(distribution.get(0).getPatientCount()).isEqualTo(4L);
        assertThat(distribution.get(1).getDepartmentName()).isEqualTo("Neurology");
    }

    // ── Cancel / leave queue (patient-initiated) ───────────────────────

    @Test
    void cancel_OwnWaitingEntry_SetsCancelled() {
        QueueEntry entry = new QueueEntry();
        entry.setId(1L);
        entry.setPatientId(PATIENT);
        entry.setDoctorCatalogEntryId(10L);
        entry.setSymptomText("changed plans");
        entry.setAiSuggestedTriage(TriageLevel.NORMAL);
        entry.setStatus(QueueStatus.WAITING);
        entry.setJoinedAt(LocalDateTime.of(2026, 8, 1, 9, 0));

        given(queueEntryRepository.findById(1L)).willReturn(Optional.of(entry));
        given(doctorServiceClient.getDoctorById(10L)).willReturn(availableDoctor());
        given(queueEntryRepository.save(any(QueueEntry.class))).willAnswer(inv -> inv.getArgument(0));

        QueueEntryResponseDto cancelled = queueService.cancel(1L, PATIENT, "PATIENT");

        assertThat(cancelled.getStatus()).isEqualTo(QueueStatus.CANCELLED);
        // No longer active — no derived position.
        assertThat(cancelled.getPosition()).isNull();
        // Still enriched with doctor details for history UIs.
        assertThat(cancelled.getDepartmentName()).isEqualTo("Cardiology");
    }

    @Test
    void cancel_AnotherPatientsEntry_Throws() {
        QueueEntry entry = new QueueEntry();
        entry.setId(1L);
        entry.setPatientId(PATIENT);
        entry.setDoctorCatalogEntryId(10L);
        entry.setSymptomText("fever");
        entry.setAiSuggestedTriage(TriageLevel.NORMAL);
        entry.setStatus(QueueStatus.WAITING);

        given(queueEntryRepository.findById(1L)).willReturn(Optional.of(entry));

        assertThatThrownBy(() -> queueService.cancel(1L, "someone-else", "PATIENT"))
                .isInstanceOf(UnauthorizedAccessException.class);
        verify(queueEntryRepository, never()).save(any(QueueEntry.class));
    }

    @Test
    void cancel_DoctorRole_Throws() {
        QueueEntry entry = new QueueEntry();
        entry.setId(1L);
        entry.setPatientId(PATIENT);
        entry.setDoctorCatalogEntryId(10L);
        entry.setSymptomText("fever");
        entry.setAiSuggestedTriage(TriageLevel.NORMAL);
        entry.setStatus(QueueStatus.WAITING);

        given(queueEntryRepository.findById(1L)).willReturn(Optional.of(entry));

        assertThatThrownBy(() -> queueService.cancel(1L, DOCTOR_USER, "DOCTOR"))
                .isInstanceOf(UnauthorizedAccessException.class);
    }

    @Test
    void cancel_Admin_CanCancelAnyEntry() {
        QueueEntry entry = new QueueEntry();
        entry.setId(1L);
        entry.setPatientId("someone-else");
        entry.setDoctorCatalogEntryId(10L);
        entry.setSymptomText("fever");
        entry.setAiSuggestedTriage(TriageLevel.NORMAL);
        entry.setStatus(QueueStatus.WAITING);

        given(queueEntryRepository.findById(1L)).willReturn(Optional.of(entry));
        given(doctorServiceClient.getDoctorById(10L)).willReturn(availableDoctor());
        given(queueEntryRepository.save(any(QueueEntry.class))).willAnswer(inv -> inv.getArgument(0));

        // Admin can cancel any entry (passes verifyPatientAccess).
        QueueEntryResponseDto cancelled = queueService.cancel(1L, ADMIN, "ADMIN");

        assertThat(cancelled.getStatus()).isEqualTo(QueueStatus.CANCELLED);
    }

    @Test
    void cancel_InProgressEntry_Throws() {
        QueueEntry entry = new QueueEntry();
        entry.setId(1L);
        entry.setPatientId(PATIENT);
        entry.setDoctorCatalogEntryId(10L);
        entry.setSymptomText("fever");
        entry.setAiSuggestedTriage(TriageLevel.NORMAL);
        entry.setStatus(QueueStatus.IN_PROGRESS);
        entry.setCalledAt(LocalDateTime.of(2026, 8, 1, 9, 10));

        given(queueEntryRepository.findById(1L)).willReturn(Optional.of(entry));

        assertThatThrownBy(() -> queueService.cancel(1L, PATIENT, "PATIENT"))
                .isInstanceOf(InvalidQueueStateException.class);
        verify(queueEntryRepository, never()).save(any(QueueEntry.class));
    }

    // ── Patient history (dashboard upgrade) ────────────────────────────

    @Test
    void getMyHistory_ReturnsCompletedAndCancelledEntriesNewestFirst() {
        QueueEntry recent = new QueueEntry();
        recent.setId(2L);
        recent.setPatientId(PATIENT);
        recent.setPatientName("Ada Patient");
        recent.setDoctorCatalogEntryId(10L);
        recent.setSymptomText("fever");
        recent.setAiSuggestedTriage(TriageLevel.NORMAL);
        recent.setStatus(QueueStatus.COMPLETED);
        recent.setJoinedAt(java.time.LocalDateTime.now().minusHours(2));
        recent.setCompletedAt(java.time.LocalDateTime.now().minusHours(1));

        QueueEntry older = new QueueEntry();
        older.setId(1L);
        older.setPatientId(PATIENT);
        older.setDoctorCatalogEntryId(10L);
        older.setSymptomText("cancelled visit");
        older.setAiSuggestedTriage(TriageLevel.NORMAL);
        older.setStatus(QueueStatus.CANCELLED);
        older.setJoinedAt(java.time.LocalDateTime.now().minusDays(3));

        given(queueEntryRepository.findHistoryByPatientId(eq(PATIENT), anyList(), any(Pageable.class)))
                .willReturn(List.of(recent, older));
        given(doctorServiceClient.getDoctorById(10L)).willReturn(availableDoctor());

        List<QueueEntryResponseDto> history = queueService.getMyHistory(PATIENT, 10);

        assertThat(history).hasSize(2);
        assertThat(history.get(0).getId()).isEqualTo(2L);
        assertThat(history.get(0).getStatus()).isEqualTo(QueueStatus.COMPLETED);
        assertThat(history.get(1).getStatus()).isEqualTo(QueueStatus.CANCELLED);
        // Enriched with the doctor's display details (and no derived position for completed entries).
        assertThat(history.get(0).getDepartmentName()).isEqualTo("Cardiology");
        assertThat(history.get(0).getPosition()).isNull();
        // History must be requested newest-first and capped.
        verify(queueEntryRepository).findHistoryByPatientId(
                eq(PATIENT),
                eq(List.of(QueueStatus.COMPLETED, QueueStatus.CANCELLED)),
                any(Pageable.class));
    }

    @Test
    void getMyHistory_DoctorServiceDown_StillReturnsEntriesWithoutDoctorDetails() {
        QueueEntry entry = new QueueEntry();
        entry.setId(1L);
        entry.setPatientId(PATIENT);
        entry.setDoctorCatalogEntryId(10L);
        entry.setSymptomText("cough");
        entry.setAiSuggestedTriage(TriageLevel.NORMAL);
        entry.setStatus(QueueStatus.COMPLETED);
        entry.setJoinedAt(java.time.LocalDateTime.now().minusDays(1));

        given(queueEntryRepository.findHistoryByPatientId(eq(PATIENT), anyList(), any(Pageable.class)))
                .willReturn(List.of(entry));
        given(doctorServiceClient.getDoctorById(10L))
                .willThrow(new RuntimeException("connection refused"));

        List<QueueEntryResponseDto> history = queueService.getMyHistory(PATIENT, 10);

        // History degrades gracefully — the visit is still listed, doctor fields stay null.
        assertThat(history).hasSize(1);
        assertThat(history.get(0).getStatus()).isEqualTo(QueueStatus.COMPLETED);
        assertThat(history.get(0).getDoctorName()).isNull();
        assertThat(history.get(0).getDepartmentName()).isNull();
    }

    // ── Per-doctor analytics (dashboard upgrade) ──────────────────────

    @Test
    void getDoctorAnalyticsSummary_EmptyData_ReturnsZeroFilledWindowAndNullTodayScalars() {
        given(doctorServiceClient.getDoctorById(10L)).willReturn(availableDoctor());
        given(queueEntryRepository.countCompletedSince(eq(10L), any())).willReturn(0L);
        given(queueEntryRepository.avgCalledWaitSince(eq(10L), any())).willReturn(null);
        given(queueEntryRepository.avgConsultDurationSince(eq(10L), any())).willReturn(null);
        given(queueEntryRepository.countCompletedPerDaySinceForDoctor(eq(10L), any())).willReturn(List.of());
        given(queueEntryRepository.avgCalledWaitPerDaySinceForDoctor(eq(10L), any())).willReturn(List.of());

        DoctorAnalyticsSummaryDto summary =
                queueService.getDoctorAnalyticsSummary(10L, DOCTOR_USER, "DOCTOR");

        assertThat(summary.getPatientsCompletedToday()).isZero();
        assertThat(summary.getAvgWaitTodayMinutes()).isNull();
        assertThat(summary.getAvgConsultTimeTodayMinutes()).isNull();
        assertThat(summary.getPatientsPerDay()).hasSize(7);
        assertThat(summary.getPatientsPerDay()).allSatisfy(d -> assertThat(d.getCount()).isZero());
        assertThat(summary.getAvgWaitTimeTrend()).hasSize(7);
        assertThat(summary.getAvgWaitTimeTrend()).allSatisfy(d -> assertThat(d.getAvgWaitMinutes()).isNull());
    }

    @Test
    void getDoctorAnalyticsSummary_PopulatedData_AggregatesAndAllowsAdmin() {
        java.time.LocalDate today = java.time.LocalDate.now();
        String todayStr = today.toString();

        DayCountProjection todayCount = mock(DayCountProjection.class);
        given(todayCount.getDay()).willReturn(todayStr);
        given(todayCount.getCnt()).willReturn(4L);

        DayAvgWaitProjection todayWait = mock(DayAvgWaitProjection.class);
        given(todayWait.getDay()).willReturn(todayStr);
        given(todayWait.getAvgWait()).willReturn(10.5);

        given(doctorServiceClient.getDoctorById(10L)).willReturn(availableDoctor());
        given(queueEntryRepository.countCompletedSince(eq(10L), any())).willReturn(4L);
        given(queueEntryRepository.avgCalledWaitSince(eq(10L), any())).willReturn(10.5);
        given(queueEntryRepository.avgConsultDurationSince(eq(10L), any())).willReturn(14.0);
        given(queueEntryRepository.countCompletedPerDaySinceForDoctor(eq(10L), any()))
                .willReturn(List.of(todayCount));
        given(queueEntryRepository.avgCalledWaitPerDaySinceForDoctor(eq(10L), any()))
                .willReturn(List.of(todayWait));

        // Admin can read any doctor's analytics (passes verifyDoctorAccess).
        DoctorAnalyticsSummaryDto summary =
                queueService.getDoctorAnalyticsSummary(10L, ADMIN, "ADMIN");

        assertThat(summary.getPatientsCompletedToday()).isEqualTo(4L);
        assertThat(summary.getAvgWaitTodayMinutes()).isEqualTo(10.5);
        assertThat(summary.getAvgConsultTimeTodayMinutes()).isEqualTo(14.0);
        assertThat(summary.getPatientsPerDay().stream()
                .filter(d -> d.getDate().equals(today)).findFirst().orElseThrow().getCount()).isEqualTo(4L);
        assertThat(summary.getAvgWaitTimeTrend().stream()
                .filter(d -> d.getDate().equals(today)).findFirst().orElseThrow().getAvgWaitMinutes()).isEqualTo(10.5);
    }

    @Test
    void getDoctorAnalyticsSummary_AnotherDoctor_Throws() {
        given(doctorServiceClient.getDoctorById(10L)).willReturn(availableDoctor());

        assertThatThrownBy(() -> queueService.getDoctorAnalyticsSummary(10L, "someone-else", "DOCTOR"))
                .isInstanceOf(UnauthorizedAccessException.class);
    }

    @Test
    void getAnalyticsSummary_DoctorServiceDown_DistributionDegradesToEmpty() {
        given(queueEntryRepository.countCompletedPerDaySince(any())).willReturn(List.of());
        given(queueEntryRepository.avgCalledWaitPerDaySince(any())).willReturn(List.of());
        given(doctorServiceClient.getAllDoctors(0, 1000)).willThrow(new RuntimeException("connection refused"));

        AnalyticsSummaryDto summary = queueService.getAnalyticsSummary();

        // Time series survive; only the department slice is degraded.
        assertThat(summary.getPatientsPerDay()).hasSize(7);
        assertThat(summary.getDepartmentDistribution()).isEmpty();
    }
}
