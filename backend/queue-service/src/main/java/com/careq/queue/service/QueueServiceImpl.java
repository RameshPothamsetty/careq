package com.careq.queue.service;

import com.careq.queue.client.DoctorServiceClient;
import com.careq.queue.dto.DoctorCatalogResponseDto;
import com.careq.queue.dto.DoctorQueueStatsDto;
import com.careq.queue.dto.JoinQueueRequestDto;
import com.careq.queue.dto.LiveQueueOverviewDto;
import com.careq.queue.dto.OverrideTriageRequestDto;
import com.careq.queue.dto.QueueEntryResponseDto;
import com.careq.queue.dto.QueueStatusResponseDto;
import com.careq.queue.entity.QueueEntry;
import com.careq.queue.entity.QueueStatus;
import com.careq.queue.entity.TriageLevel;
import com.careq.queue.exception.DoctorCatalogNotFoundException;
import com.careq.queue.exception.DoctorServiceUnavailableException;
import com.careq.queue.exception.DoctorUnavailableException;
import com.careq.queue.exception.DuplicateQueueEntryException;
import com.careq.queue.exception.InvalidQueueStateException;
import com.careq.queue.exception.QueueEntryNotFoundException;
import com.careq.queue.exception.UnauthorizedAccessException;
import com.careq.queue.repository.QueueEntryRepository;
import feign.FeignException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
public class QueueServiceImpl implements QueueService {

    private static final Logger log = LoggerFactory.getLogger(QueueServiceImpl.class);

    private final QueueEntryRepository queueEntryRepository;
    private final DoctorServiceClient doctorServiceClient;
    private final AiTriageService aiTriageService;
    private final QueueOrderingService orderingService;

    /** A WAITING patient is "delayed" in the Admin overview when their predicted wait exceeds this. */
    private final int delayThresholdMinutes;

    public QueueServiceImpl(QueueEntryRepository queueEntryRepository,
                            DoctorServiceClient doctorServiceClient,
                            AiTriageService aiTriageService,
                            QueueOrderingService orderingService,
                            @Value("${queue.delay-threshold-minutes:30}") int delayThresholdMinutes) {
        this.queueEntryRepository = queueEntryRepository;
        this.doctorServiceClient = doctorServiceClient;
        this.aiTriageService = aiTriageService;
        this.orderingService = orderingService;
        this.delayThresholdMinutes = delayThresholdMinutes;
    }

    @Override
    @Transactional
    public QueueEntryResponseDto joinQueue(String patientId, JoinQueueRequestDto request) {
        // 1. Fetch the doctor's live data via Feign — single source of truth.
        DoctorCatalogResponseDto doctor = fetchDoctor(request.getDoctorCatalogEntryId());

        // 2. A patient cannot join the queue of an unavailable doctor.
        if (!Boolean.TRUE.equals(doctor.getIsAvailable())) {
            throw new DoctorUnavailableException(
                    "Doctor is currently unavailable — please choose another doctor");
        }

        // 3. A patient cannot hold two active entries with the same doctor.
        boolean alreadyWaiting = queueEntryRepository
                .existsByPatientIdAndDoctorCatalogEntryIdAndStatusIn(
                        patientId, request.getDoctorCatalogEntryId(), QueueOrderingService.ACTIVE_STATUSES);
        if (alreadyWaiting) {
            throw new DuplicateQueueEntryException(
                    "You already have an active queue entry for this doctor");
        }

        // 4. AI symptom triage — always degrades gracefully to NORMAL.
        TriageLevel triage = aiTriageService.classifyWithFallback(request.getSymptomText());

        // 5. Persist the entry.
        QueueEntry entry = new QueueEntry();
        entry.setPatientId(patientId);
        entry.setDoctorCatalogEntryId(request.getDoctorCatalogEntryId());
        entry.setSymptomText(request.getSymptomText().trim());
        entry.setAiSuggestedTriage(triage);
        entry.setStatus(QueueStatus.WAITING);
        entry.setJoinedAt(LocalDateTime.now());
        entry = queueEntryRepository.save(entry);

        // 6. Recompute ordering (new patient joined) and return with position + predicted wait.
        return toResponseDto(entry, doctor);
    }

    @Override
    @Transactional(readOnly = true)
    public QueueStatusResponseDto getMyStatus(String patientId) {
        return queueEntryRepository
                .findFirstByPatientIdAndStatusInOrderByJoinedAtDesc(
                        patientId, QueueOrderingService.ACTIVE_STATUSES)
                .map(entry -> {
                    DoctorCatalogResponseDto doctor = fetchDoctor(entry.getDoctorCatalogEntryId());
                    return new QueueStatusResponseDto(true, toResponseDto(entry, doctor));
                })
                .orElseGet(() -> new QueueStatusResponseDto(false, null));
    }

    @Override
    @Transactional(readOnly = true)
    public List<QueueEntryResponseDto> getDoctorQueue(Long doctorCatalogEntryId,
                                                      String requesterUserId,
                                                      String requesterRole) {
        DoctorCatalogResponseDto doctor = fetchDoctor(doctorCatalogEntryId);
        verifyDoctorAccess(doctor, requesterUserId, requesterRole);

        List<QueueEntry> active = queueEntryRepository.findByDoctorCatalogEntryIdAndStatusInOrderByJoinedAtAsc(
                doctorCatalogEntryId, QueueOrderingService.ACTIVE_STATUSES);
        List<QueueEntry> ordered = orderingService.orderByEffectivePriority(active);

        return ordered.stream()
                .map(e -> toResponseDto(e, doctor))
                .collect(Collectors.toList());
    }

    @Override
    @Transactional
    public QueueEntryResponseDto overrideTriage(Long queueEntryId, OverrideTriageRequestDto request,
                                                String requesterUserId, String requesterRole) {
        QueueEntry entry = getEntryOrThrow(queueEntryId);
        DoctorCatalogResponseDto doctor = fetchDoctor(entry.getDoctorCatalogEntryId());
        verifyDoctorAccess(doctor, requesterUserId, requesterRole);

        // Can't override triage on a completed/cancelled/in-progress entry — only WAITING.
        if (entry.getStatus() != QueueStatus.WAITING) {
            throw new InvalidQueueStateException(
                    "Cannot override triage on an entry with status: " + entry.getStatus());
        }

        entry.setDoctorOverrideTriage(request.getTriageLevel());
        entry = queueEntryRepository.save(entry);
        return toResponseDto(entry, doctor);
    }

    @Override
    @Transactional
    public QueueEntryResponseDto callNext(Long queueEntryId, String requesterUserId, String requesterRole) {
        QueueEntry entry = getEntryOrThrow(queueEntryId);
        DoctorCatalogResponseDto doctor = fetchDoctor(entry.getDoctorCatalogEntryId());
        verifyDoctorAccess(doctor, requesterUserId, requesterRole);

        if (entry.getStatus() != QueueStatus.WAITING) {
            throw new InvalidQueueStateException(
                    "Only a WAITING patient can be called next (current status: " + entry.getStatus() + ")");
        }

        entry.setStatus(QueueStatus.IN_PROGRESS);
        entry.setCalledAt(LocalDateTime.now());
        entry = queueEntryRepository.save(entry);
        return toResponseDto(entry, doctor);
    }

    @Override
    @Transactional
    public QueueEntryResponseDto complete(Long queueEntryId, String requesterUserId, String requesterRole) {
        QueueEntry entry = getEntryOrThrow(queueEntryId);
        DoctorCatalogResponseDto doctor = fetchDoctor(entry.getDoctorCatalogEntryId());
        verifyDoctorAccess(doctor, requesterUserId, requesterRole);

        if (entry.getStatus() != QueueStatus.IN_PROGRESS) {
            throw new InvalidQueueStateException(
                    "Only an IN_PROGRESS consultation can be completed (current status: " + entry.getStatus() + ")");
        }

        entry.setStatus(QueueStatus.COMPLETED);
        entry.setCompletedAt(LocalDateTime.now());
        entry = queueEntryRepository.save(entry);
        return toResponseDto(entry, doctor);
    }

    @Override
    @Transactional(readOnly = true)
    public LiveQueueOverviewDto getLiveOverview() {
        // All doctors (availability comes live from doctor-service).
        List<DoctorCatalogResponseDto> doctors = fetchAllDoctors();

        // All active queue entries, grouped per doctor.
        Map<Long, List<QueueEntry>> entriesByDoctor = queueEntryRepository
                .findAllByStatusIn(QueueOrderingService.ACTIVE_STATUSES)
                .stream()
                .collect(Collectors.groupingBy(QueueEntry::getDoctorCatalogEntryId));

        LiveQueueOverviewDto overview = new LiveQueueOverviewDto();
        List<DoctorQueueStatsDto> statsList = new ArrayList<>();

        int totalWaiting = 0;
        int totalInProgress = 0;
        int delayedConsultations = 0;
        long doctorsOnline = 0;
        long totalPredictedWait = 0;
        int waitingWithPrediction = 0;

        for (DoctorCatalogResponseDto doctor : doctors) {
            List<QueueEntry> entries = entriesByDoctor.getOrDefault(doctor.getId(), List.of());
            List<QueueEntry> ordered = orderingService.orderByEffectivePriority(entries);

            DoctorQueueStatsDto stats = new DoctorQueueStatsDto();
            stats.setDoctorCatalogEntryId(doctor.getId());
            stats.setDoctorName(doctor.getName());
            stats.setDoctorUserId(doctor.getUserId());
            stats.setDepartmentName(doctor.getDepartmentName());
            stats.setSpecialization(doctor.getSpecialization());
            stats.setAvgConsultationTimeMinutes(doctor.getAvgConsultationTimeMinutes());
            stats.setIsAvailable(doctor.getIsAvailable());

            int waiting = 0;
            int inProgress = 0;
            int delayed = 0;
            int longestWait = 0;

            for (int i = 0; i < ordered.size(); i++) {
                QueueEntry e = ordered.get(i);
                int position = i + 1;
                int predictedWait = orderingService.predictedWaitMinutes(
                        position, doctor.getAvgConsultationTimeMinutes());

                if (e.getStatus() == QueueStatus.WAITING) {
                    waiting++;
                    totalPredictedWait += predictedWait;
                    waitingWithPrediction++;
                    if (predictedWait > delayThresholdMinutes) {
                        delayed++;
                        delayedConsultations++;
                    }
                    longestWait = Math.max(longestWait, predictedWait);
                } else if (e.getStatus() == QueueStatus.IN_PROGRESS) {
                    inProgress++;
                }
            }

            stats.setWaitingCount(waiting);
            stats.setInProgressCount(inProgress);
            stats.setDelayedCount(delayed);
            stats.setLongestWaitMinutes(longestWait);

            totalWaiting += waiting;
            totalInProgress += inProgress;
            if (Boolean.TRUE.equals(doctor.getIsAvailable())) {
                doctorsOnline++;
            }
            statsList.add(stats);
        }

        overview.setTotalWaiting(totalWaiting);
        overview.setTotalInProgress(totalInProgress);
        overview.setDoctorsOnline(doctorsOnline);
        overview.setDoctorsOffline(doctors.size() - doctorsOnline);
        overview.setDelayedConsultations(delayedConsultations);
        overview.setAverageWaitMinutes(
                waitingWithPrediction == 0 ? 0 : (int) Math.round((double) totalPredictedWait / waitingWithPrediction));
        overview.setDoctors(statsList);
        return overview;
    }

    // ─────────────────────────────────────────────────────────────
    // Helpers
    // ─────────────────────────────────────────────────────────────

    private QueueEntry getEntryOrThrow(Long id) {
        return queueEntryRepository.findById(id)
                .orElseThrow(() -> new QueueEntryNotFoundException("Queue entry not found with id: " + id));
    }

    /**
     * Builds the response DTO with a LIVE recalculated position and predicted
     * wait (never cached — the queue changes constantly).
     */
    private QueueEntryResponseDto toResponseDto(QueueEntry entry, DoctorCatalogResponseDto doctor) {
        QueueEntryResponseDto dto = QueueEntryResponseDto.fromEntity(entry);
        dto.setDepartmentName(doctor.getDepartmentName());
        dto.setSpecialization(doctor.getSpecialization());
        // Doctor display name comes from the catalog entry (added Day 7a).
        dto.setDoctorName(doctor.getName());

        if (QueueOrderingService.ACTIVE_STATUSES.contains(entry.getStatus())) {
            List<QueueEntry> active = queueEntryRepository
                    .findByDoctorCatalogEntryIdAndStatusInOrderByJoinedAtAsc(
                            entry.getDoctorCatalogEntryId(), QueueOrderingService.ACTIVE_STATUSES);
            List<QueueEntry> ordered = orderingService.orderByEffectivePriority(active);
            int position = orderingService.positionOf(entry, ordered);
            dto.setPosition(position);
            dto.setPredictedWaitMinutes(
                    orderingService.predictedWaitMinutes(position, doctor.getAvgConsultationTimeMinutes()));
        }
        return dto;
    }

    /** Verifies a DOCTOR only touches their own queue; ADMIN is allowed everywhere. */
    private void verifyDoctorAccess(DoctorCatalogResponseDto doctor, String requesterUserId, String requesterRole) {
        if ("ADMIN".equalsIgnoreCase(requesterRole)) {
            return;
        }
        if (!"DOCTOR".equalsIgnoreCase(requesterRole)) {
            throw new UnauthorizedAccessException("Only a doctor or an admin can manage queue entries");
        }
        if (!doctor.getUserId().equals(requesterUserId)) {
            throw new UnauthorizedAccessException("You can only manage your own queue");
        }
    }

    /** Feign wrapper: 404 from doctor-service = unknown doctor; anything else = service down. */
    private DoctorCatalogResponseDto fetchDoctor(Long doctorCatalogEntryId) {
        try {
            DoctorCatalogResponseDto doctor = doctorServiceClient.getDoctorById(doctorCatalogEntryId);
            if (doctor == null || doctor.getId() == null) {
                throw new DoctorCatalogNotFoundException(
                        "Doctor catalog entry not found with id: " + doctorCatalogEntryId);
            }
            return doctor;
        } catch (FeignException.NotFound e) {
            throw new DoctorCatalogNotFoundException(
                    "Doctor catalog entry not found with id: " + doctorCatalogEntryId);
        } catch (RuntimeException e) {
            // FeignException extends RuntimeException — this covers timeouts, 5xx, and circuit issues.
            log.error("Failed to reach doctor-service for doctor id {}: {}", doctorCatalogEntryId, e.getMessage());
            throw new DoctorServiceUnavailableException(
                    "Doctor service is temporarily unavailable — please try again shortly");
        }
    }

    private List<DoctorCatalogResponseDto> fetchAllDoctors() {
        try {
            return doctorServiceClient.getAllDoctors();
        } catch (RuntimeException e) {
            log.error("Failed to reach doctor-service while building live overview: {}", e.getMessage());
            throw new DoctorServiceUnavailableException(
                    "Doctor service is temporarily unavailable — please try again shortly");
        }
    }
}
