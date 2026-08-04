package com.careq.queue.service;

import com.careq.queue.client.DoctorServiceClient;
import com.careq.queue.dto.DoctorCatalogResponseDto;
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
import com.careq.queue.repository.QueueEntryRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
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

    private final QueueOrderingService orderingService = new QueueOrderingService();

    private QueueServiceImpl queueService;

    private DoctorCatalogResponseDto availableDoctor() {
        DoctorCatalogResponseDto d = new DoctorCatalogResponseDto();
        d.setId(10L);
        d.setUserId(DOCTOR_USER);
        d.setSpecialization("Cardiology");
        d.setDepartmentName("Cardiology");
        d.setAvgConsultationTimeMinutes(15);
        d.setIsAvailable(true);
        return d;
    }

    @BeforeEach
    void setUp() {
        queueService = new QueueServiceImpl(
                queueEntryRepository, doctorServiceClient, aiTriageService, orderingService, 30, 1000);
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
}
