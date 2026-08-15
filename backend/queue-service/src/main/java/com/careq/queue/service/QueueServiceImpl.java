package com.careq.queue.service;

import com.careq.queue.client.DoctorServiceClient;
import com.careq.queue.dto.AiAssessment;
import com.careq.queue.dto.AnalyticsSummaryDto;
import com.careq.queue.dto.DailyAvgWaitDto;
import com.careq.queue.dto.DailyPatientCountDto;
import com.careq.queue.dto.DepartmentDistributionDto;
import com.careq.queue.dto.DoctorAnalyticsSummaryDto;
import com.careq.queue.dto.DoctorCatalogResponseDto;
import com.careq.queue.dto.DoctorQueueStatsDto;
import com.careq.queue.dto.JoinQueueRequestDto;
import com.careq.queue.dto.LiveQueueOverviewDto;
import com.careq.queue.dto.OverrideTriageRequestDto;
import com.careq.queue.dto.QueueEntryResponseDto;
import com.careq.queue.dto.QueueEventDto;
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
import com.careq.queue.repository.DayAvgWaitProjection;
import com.careq.queue.repository.DayCountProjection;
import com.careq.queue.repository.DoctorCountProjection;
import com.careq.queue.repository.QueueEntryRepository;
import feign.FeignException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
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
    private final QueueEventPublisher eventPublisher;

    /** A WAITING patient is "delayed" in the Admin overview when their predicted wait exceeds this. */
    private final int delayThresholdMinutes;

    /**
     * Upper bound on how many doctors the Admin overview loads in one page.
     * The live overview needs the whole catalog; 1000 is far beyond any real
     * hospital catalog and avoids looping pages.
     */
    private final int maxDoctorsToLoad;

    public QueueServiceImpl(QueueEntryRepository queueEntryRepository,
                            DoctorServiceClient doctorServiceClient,
                            AiTriageService aiTriageService,
                            QueueOrderingService orderingService,
                            QueueEventPublisher eventPublisher,
                            @Value("${queue.delay-threshold-minutes:30}") int delayThresholdMinutes,
                            @Value("${queue.max-doctors-to-load:1000}") int maxDoctorsToLoad) {
        this.queueEntryRepository = queueEntryRepository;
        this.doctorServiceClient = doctorServiceClient;
        this.aiTriageService = aiTriageService;
        this.orderingService = orderingService;
        this.eventPublisher = eventPublisher;
        this.delayThresholdMinutes = delayThresholdMinutes;
        this.maxDoctorsToLoad = maxDoctorsToLoad;
    }

    @Override
    @Transactional
    public QueueEntryResponseDto joinQueue(String patientId, JoinQueueRequestDto request) {
        return joinQueueInternal(patientId, request, null);
    }

    @Override
    @Transactional
    public QueueEntryResponseDto joinQueueWithAssessment(String patientId, JoinQueueRequestDto request,
                                                         AiAssessment assessment) {
        return joinQueueInternal(patientId, request, assessment);
    }

    private QueueEntryResponseDto joinQueueInternal(String patientId, JoinQueueRequestDto request,
                                                    AiAssessment precomputed) {
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

        // 4. AI symptom triage — always degrades gracefully to NORMAL. The
        //    auto-assign flow passes the assessment it already computed, so the
        //    LLM is called once and the persisted triage matches what the UI
        //    showed the patient.
        TriageLevel triage = precomputed != null
                ? precomputed.triage()
                : aiTriageService.classifyWithFallback(request.getSymptomText());

        // 5. Persist the entry.
        QueueEntry entry = new QueueEntry();
        entry.setPatientId(patientId);
        entry.setPatientName(request.getPatientName() == null ? null : request.getPatientName().trim());
        entry.setDoctorCatalogEntryId(request.getDoctorCatalogEntryId());
        entry.setSymptomText(request.getSymptomText().trim());
        entry.setAiSuggestedTriage(triage);
        entry.setStatus(QueueStatus.WAITING);
        entry.setJoinedAt(LocalDateTime.now());
        entry = queueEntryRepository.save(entry);

        // 6. Recompute ordering (new patient joined) and return with position + predicted wait.
        QueueEntryResponseDto response = toResponseDto(entry, doctor);

        // post-decision event — never on the critical path (publishing
        // failures are caught inside QueueEventPublisher and only logged).
        eventPublisher.publish(QueueEventPublisher.QUEUE_JOINED,
                queueEvent(QueueEventPublisher.QUEUE_JOINED, entry, doctor, response.getPosition(), triage));

        return response;
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
    public List<QueueEntryResponseDto> getMyHistory(String patientId, int limit) {
        return queueEntryRepository
                .findHistoryByPatientId(patientId,
                        List.of(QueueStatus.COMPLETED, QueueStatus.CANCELLED),
                        PageRequest.of(0, limit))
                .stream()
                .map(entry -> {
                    try {
                        return toResponseDto(entry, fetchDoctor(entry.getDoctorCatalogEntryId()));
                    } catch (RuntimeException e) {
                        // doctor-service down: the visit still shows in history,
                        // just without the doctor's display details.
                        log.warn("Failed to enrich history entry {} with doctor details: {}",
                                entry.getId(), e.getMessage());
                        return QueueEntryResponseDto.fromEntity(entry);
                    }
                })
                .collect(Collectors.toList());
    }

    @Override
    @Transactional(readOnly = true)
    public List<QueueEntryResponseDto> getDoctorQueue(Long doctorCatalogEntryId, String search,
                                                      String requesterUserId,
                                                      String requesterRole) {
        DoctorCatalogResponseDto doctor = fetchDoctor(doctorCatalogEntryId);
        verifyDoctorAccess(doctor, requesterUserId, requesterRole);

        List<QueueEntry> active = queueEntryRepository.findByDoctorCatalogEntryIdAndStatusInOrderByJoinedAtAsc(
                doctorCatalogEntryId, QueueOrderingService.ACTIVE_STATUSES);
        List<QueueEntry> ordered = orderingService.orderByEffectivePriority(active);

        // Optional patient-name filter (case-insensitive substring). Positions
        // reported by toResponseDto stay the REAL queue positions — the search
        // only narrows which rows are returned, never their ordering.
        java.util.stream.Stream<QueueEntry> stream = ordered.stream();
        if (search != null && !search.isBlank()) {
            String q = search.trim().toLowerCase();
            stream = stream.filter(e -> e.getPatientName() != null
                    && e.getPatientName().toLowerCase().contains(q));
        }
        return stream
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

        // the patient learns their (possibly reordered) urgency.
        eventPublisher.publish(QueueEventPublisher.QUEUE_TRIAGED,
                queueEvent(QueueEventPublisher.QUEUE_TRIAGED, entry, doctor, null, entry.effectiveTriage()));

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

        // "It's your turn!" — the flagship transition.
        eventPublisher.publish(QueueEventPublisher.QUEUE_CALLED,
                queueEvent(QueueEventPublisher.QUEUE_CALLED, entry, doctor, null, entry.effectiveTriage()));

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

        // consultation finished.
        eventPublisher.publish(QueueEventPublisher.QUEUE_COMPLETED,
                queueEvent(QueueEventPublisher.QUEUE_COMPLETED, entry, doctor, null, entry.effectiveTriage()));

        return toResponseDto(entry, doctor);
    }

    @Override
    @Transactional
    public QueueEntryResponseDto cancel(Long queueEntryId, String requesterUserId, String requesterRole) {
        QueueEntry entry = getEntryOrThrow(queueEntryId);
        verifyPatientAccess(entry, requesterUserId, requesterRole);

        // Only a patient still waiting can leave — a consultation in progress
        // is finished by the doctor, never cancelled by the patient.
        if (entry.getStatus() != QueueStatus.WAITING) {
            throw new InvalidQueueStateException(
                    "Only a WAITING entry can be cancelled (current status: " + entry.getStatus() + ")");
        }

        // Fetch the doctor BEFORE mutating (same order as the other mutations)
        // so a doctor-service failure aborts before any state change — never a
        // save-then-503 inconsistency.
        DoctorCatalogResponseDto doctor = fetchDoctor(entry.getDoctorCatalogEntryId());

        entry.setStatus(QueueStatus.CANCELLED);
        entry = queueEntryRepository.save(entry);

        // CANCELLED is not an active status, so no derived position/wait — the
        // response is enriched with the doctor's display details for history UIs.
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

    @Override
    @Transactional(readOnly = true)
    public AnalyticsSummaryDto getAnalyticsSummary() {
        // Window: the last 7 days inclusive of today.
        LocalDate today = LocalDate.now();
        LocalDate start = today.minusDays(6);
        LocalDateTime since = start.atStartOfDay();

        // 1. Patients handled per day (entries completed that day) — zero-filled.
        Map<LocalDate, Long> completedByDay = queueEntryRepository
                .countCompletedPerDaySince(since)
                .stream()
                .collect(Collectors.toMap(p -> LocalDate.parse(p.getDay()),
                        DayCountProjection::getCnt, Long::sum));
        List<DailyPatientCountDto> patientsPerDay = new ArrayList<>();
        for (LocalDate d = start; !d.isAfter(today); d = d.plusDays(1)) {
            patientsPerDay.add(new DailyPatientCountDto(d, completedByDay.getOrDefault(d, 0L)));
        }

        // 2. Average wait trend (entries called that day) — null when no calls happened.
        Map<LocalDate, Double> avgWaitByDay = queueEntryRepository
                .avgCalledWaitPerDaySince(since)
                .stream()
                .collect(Collectors.toMap(p -> LocalDate.parse(p.getDay()),
                        DayAvgWaitProjection::getAvgWait));
        List<DailyAvgWaitDto> avgWaitTrend = new ArrayList<>();
        for (LocalDate d = start; !d.isAfter(today); d = d.plusDays(1)) {
            avgWaitTrend.add(new DailyAvgWaitDto(d, avgWaitByDay.get(d)));
        }

        AnalyticsSummaryDto summary = new AnalyticsSummaryDto();
        summary.setPatientsPerDay(patientsPerDay);
        summary.setAvgWaitTimeTrend(avgWaitTrend);
        summary.setDepartmentDistribution(buildDepartmentDistribution(since));
        return summary;
    }

    @Override
    @Transactional(readOnly = true)
    public DoctorAnalyticsSummaryDto getDoctorAnalyticsSummary(Long doctorCatalogEntryId,
                                                              String requesterUserId,
                                                              String requesterRole) {
        // Same ownership rule as the live queue: doctor reads only their own
        // entry, admin can read any doctor's.
        DoctorCatalogResponseDto doctor = fetchDoctor(doctorCatalogEntryId);
        verifyDoctorAccess(doctor, requesterUserId, requesterRole);

        LocalDate today = LocalDate.now();
        LocalDateTime startOfToday = today.atStartOfDay();
        LocalDateTime since = today.minusDays(6).atStartOfDay();

        DoctorAnalyticsSummaryDto summary = new DoctorAnalyticsSummaryDto();
        summary.setPatientsCompletedToday(
                queueEntryRepository.countCompletedSince(doctorCatalogEntryId, startOfToday));
        summary.setAvgWaitTodayMinutes(
                queueEntryRepository.avgCalledWaitSince(doctorCatalogEntryId, startOfToday));
        summary.setAvgConsultTimeTodayMinutes(
                queueEntryRepository.avgConsultDurationSince(doctorCatalogEntryId, startOfToday));

        // 7-day trends, zero-filled (mirrors the admin analytics window).
        Map<LocalDate, Long> completedByDay = queueEntryRepository
                .countCompletedPerDaySinceForDoctor(doctorCatalogEntryId, since)
                .stream()
                .collect(Collectors.toMap(p -> LocalDate.parse(p.getDay()),
                        DayCountProjection::getCnt, Long::sum));
        List<DailyPatientCountDto> patientsPerDay = new ArrayList<>();
        for (LocalDate d = today.minusDays(6); !d.isAfter(today); d = d.plusDays(1)) {
            patientsPerDay.add(new DailyPatientCountDto(d, completedByDay.getOrDefault(d, 0L)));
        }
        summary.setPatientsPerDay(patientsPerDay);

        Map<LocalDate, Double> avgWaitByDay = queueEntryRepository
                .avgCalledWaitPerDaySinceForDoctor(doctorCatalogEntryId, since)
                .stream()
                .collect(Collectors.toMap(p -> LocalDate.parse(p.getDay()),
                        DayAvgWaitProjection::getAvgWait));
        List<DailyAvgWaitDto> avgWaitTrend = new ArrayList<>();
        for (LocalDate d = today.minusDays(6); !d.isAfter(today); d = d.plusDays(1)) {
            avgWaitTrend.add(new DailyAvgWaitDto(d, avgWaitByDay.get(d)));
        }
        summary.setAvgWaitTimeTrend(avgWaitTrend);
        return summary;
    }

    /**
     * Department distribution over the analytics window. Completion counts
     * are aggregated per doctor in SQL, then mapped to department names via
     * the Feign doctor catalog (single source of truth). If doctor-service
     * is unreachable the distribution degrades to an empty list rather than
     * failing the whole analytics response — the time series still work.
     */
    private List<DepartmentDistributionDto> buildDepartmentDistribution(LocalDateTime since) {
        try {
            Map<Long, String> departmentByDoctor = fetchAllDoctors()
                    .stream()
                    .collect(Collectors.toMap(DoctorCatalogResponseDto::getId,
                            DoctorCatalogResponseDto::getDepartmentName, (a, b) -> a));

            Map<String, Long> countsByDepartment = new HashMap<>();
            for (DoctorCountProjection p : queueEntryRepository.countCompletedPerDoctorSince(since)) {
                String department = departmentByDoctor.getOrDefault(p.getDoctorCatalogEntryId(), "Unknown");
                countsByDepartment.merge(department, p.getCnt(), Long::sum);
            }
            return countsByDepartment.entrySet()
                    .stream()
                    .sorted(Map.Entry.comparingByValue(Comparator.reverseOrder()))
                    .map(e -> new DepartmentDistributionDto(e.getKey(), e.getValue()))
                    .collect(Collectors.toList());
        } catch (RuntimeException e) {
            log.error("Failed to build department distribution (doctor-service down?): {}", e.getMessage());
            return new ArrayList<>();
        }
    }

    // ─────────────────────────────────────────────────────────────
    // Helpers
    // ─────────────────────────────────────────────────────────────

    /** builds a notification event from a persisted queue entry + doctor context. */
    private QueueEventDto queueEvent(String eventType, QueueEntry entry, DoctorCatalogResponseDto doctor,
                                     Integer position, TriageLevel triage) {
        return new QueueEventDto(
                eventType,
                entry.getPatientId(),
                entry.getId(),
                entry.getPatientName(),
                doctor.getName(),
                doctor.getDepartmentName(),
                position,
                triage == null ? null : triage.name(),
                LocalDateTime.now());
    }

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
        // Doctor display name comes from the catalog entry .
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

    /** Verifies a patient only cancels their own entry; ADMIN is allowed everywhere. */
    private void verifyPatientAccess(QueueEntry entry, String requesterUserId, String requesterRole) {
        if ("ADMIN".equalsIgnoreCase(requesterRole)) {
            return;
        }
        if (!"PATIENT".equalsIgnoreCase(requesterRole)) {
            throw new UnauthorizedAccessException("Only the patient or an admin can cancel a queue entry");
        }
        if (!entry.getPatientId().equals(requesterUserId)) {
            throw new UnauthorizedAccessException("You can only cancel your own queue entry");
        }
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
            return doctorServiceClient.getAllDoctors(0, maxDoctorsToLoad).getContent();
        } catch (RuntimeException e) {
            log.error("Failed to reach doctor-service while building live overview: {}", e.getMessage());
            throw new DoctorServiceUnavailableException(
                    "Doctor service is temporarily unavailable — please try again shortly");
        }
    }
}
