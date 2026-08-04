package com.careq.queue.service;

import com.careq.queue.entity.QueueEntry;
import com.careq.queue.entity.QueueStatus;
import com.careq.queue.entity.TriageLevel;
import org.springframework.stereotype.Service;

import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Pure queue logic: effective ordering and wait-time prediction.
 *
 * Ordering rule (Day 5):
 *   1. Sort primarily by effective triage level: EMERGENCY > HIGH > NORMAL > FOLLOW_UP
 *   2. Within the same level, FIFO by joinedAt
 *
 * Effective triage = doctorOverrideTriage if present, else aiSuggestedTriage.
 *
 * Wait-time formula (state as the product contract):
 *   predictedWaitMinutes = (number of patients ahead in effective queue order)
 *                          x doctor's avgConsultationTimeMinutes
 *
 * This service recomputes ordering on every read — nothing is cached
 * statically, because the queue changes constantly.
 */
@Service
public class QueueOrderingService {

    /** Active entries that occupy a position in the live queue. */
    public static final List<QueueStatus> ACTIVE_STATUSES =
            List.of(QueueStatus.WAITING, QueueStatus.IN_PROGRESS);

    /**
     * Returns active entries sorted by effective triage (desc), then FIFO.
     * Callers are expected to pass all entries belonging to ONE doctor's queue.
     */
    public List<QueueEntry> orderByEffectivePriority(List<QueueEntry> entries) {
        return entries.stream()
                .filter(e -> ACTIVE_STATUSES.contains(e.getStatus()))
                .sorted(Comparator
                        .comparingInt((QueueEntry e) -> effectiveTriage(e).getPriority()).reversed()
                        .thenComparing(QueueEntry::getJoinedAt))
                .collect(Collectors.toList());
    }

    /**
     * Effective triage for an entry: doctor override wins when set.
     */
    public TriageLevel effectiveTriage(QueueEntry entry) {
        return entry.getDoctorOverrideTriage() != null
                ? entry.getDoctorOverrideTriage()
                : entry.getAiSuggestedTriage();
    }

    /**
     * 1-based position of an entry within the ordered active queue.
     * Position 1 = the next patient to be seen.
     */
    public int positionOf(QueueEntry entry, List<QueueEntry> ordered) {
        for (int i = 0; i < ordered.size(); i++) {
            if (ordered.get(i).getId().equals(entry.getId())) {
                return i + 1;
            }
        }
        throw new IllegalArgumentException("Entry " + entry.getId() + " is not in the active queue");
    }

    /**
     * predictedWaitMinutes = (number of patients ahead) x avg consultation minutes.
     * Patients ahead of a 1-based position = position - 1 (includes the patient
     * currently IN_PROGRESS, if any).
     */
    public int predictedWaitMinutes(int position, Integer avgConsultationTimeMinutes) {
        int avg = avgConsultationTimeMinutes != null ? avgConsultationTimeMinutes : 0;
        int patientsAhead = Math.max(0, position - 1);
        return patientsAhead * avg;
    }
}
