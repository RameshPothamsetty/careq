package com.careq.queue.service;

import com.careq.queue.dto.AiAssessment;
import com.careq.queue.dto.AnalyticsSummaryDto;
import com.careq.queue.dto.DoctorAnalyticsSummaryDto;
import com.careq.queue.dto.JoinQueueRequestDto;
import com.careq.queue.dto.LiveQueueOverviewDto;
import com.careq.queue.dto.OverrideTriageRequestDto;
import com.careq.queue.dto.QueueEntryResponseDto;
import com.careq.queue.dto.QueueStatusResponseDto;

import java.util.List;

/**
 * Business logic for the queue module. No logic lives in controllers.
 */
public interface QueueService {

    /** Patient joins the doctor's queue; runs AI triage; returns entry with predicted wait. */
    QueueEntryResponseDto joinQueue(String patientId, JoinQueueRequestDto request);

    /**
     * Patient joins a doctor's queue using a PRE-COMPUTED AI assessment (the
     * auto-assign flow) — the triage the patient was shown is the triage that
     * is persisted, and the LLM is called once instead of twice.
     */
    QueueEntryResponseDto joinQueueWithAssessment(String patientId, JoinQueueRequestDto request,
                                                  AiAssessment assessment);

    /** Patient's own current position + freshly recalculated predicted wait time. */
    QueueStatusResponseDto getMyStatus(String patientId);

    /** Patient's visit history (completed/cancelled entries, newest first, capped by limit). */
    List<QueueEntryResponseDto> getMyHistory(String patientId, int limit);

    /** Doctor/Admin view of one doctor's live queue, ordered by effective triage then FIFO.
     *  An optional patient-name search narrows the returned rows. */
    List<QueueEntryResponseDto> getDoctorQueue(Long doctorCatalogEntryId, String search,
                                               String requesterUserId, String requesterRole);

    /** Doctor sets the final triage override (triggers reordering on next read). */
    QueueEntryResponseDto overrideTriage(Long queueEntryId, OverrideTriageRequestDto request,
                                         String requesterUserId, String requesterRole);

    /** Doctor marks the next patient as IN_PROGRESS. */
    QueueEntryResponseDto callNext(Long queueEntryId, String requesterUserId, String requesterRole);

    /** Doctor marks a patient COMPLETED. */
    QueueEntryResponseDto complete(Long queueEntryId, String requesterUserId, String requesterRole);

    /** Patient leaves the queue before being seen (WAITING only). Admin can cancel any entry. */
    QueueEntryResponseDto cancel(Long queueEntryId, String requesterUserId, String requesterRole);

    /** Admin-only live overview across all doctors. */
    LiveQueueOverviewDto getLiveOverview();

    /** Admin-only analytics summary over the last 7 days (patients/day, avg wait trend, department distribution). */
    AnalyticsSummaryDto getAnalyticsSummary();

    /**
     * Per-doctor analytics (today's scalars + 7-day trends). Doctors can only
     * read their own entry; admins can read any doctor's.
     */
    DoctorAnalyticsSummaryDto getDoctorAnalyticsSummary(Long doctorCatalogEntryId,
                                                        String requesterUserId,
                                                        String requesterRole);
}
