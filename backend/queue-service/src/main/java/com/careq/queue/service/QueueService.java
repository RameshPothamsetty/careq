package com.careq.queue.service;

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

    /** Patient's own current position + freshly recalculated predicted wait time. */
    QueueStatusResponseDto getMyStatus(String patientId);

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

    /** Admin-only live overview across all doctors. */
    LiveQueueOverviewDto getLiveOverview();
}
