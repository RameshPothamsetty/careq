package com.careq.queue.service;

import com.careq.queue.entity.QueueEntry;
import com.careq.queue.entity.QueueStatus;
import com.careq.queue.entity.TriageLevel;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for queue ordering and wait-time calculation (Day 5).
 */
class QueueOrderingServiceTest {

    private final QueueOrderingService ordering = new QueueOrderingService();

    private QueueEntry entry(long id, TriageLevel triage, LocalDateTime joinedAt, QueueStatus status) {
        QueueEntry e = new QueueEntry();
        e.setId(id);
        e.setAiSuggestedTriage(triage);
        e.setJoinedAt(joinedAt);
        e.setStatus(status);
        return e;
    }

    @Test
    void orderByEffectivePriority_SortsByTriageThenFifo() {
        LocalDateTime base = LocalDateTime.of(2026, 7, 31, 9, 0);
        QueueEntry normalEarly = entry(1, TriageLevel.NORMAL, base, QueueStatus.WAITING);
        QueueEntry emergency = entry(2, TriageLevel.EMERGENCY, base, QueueStatus.WAITING);
        QueueEntry high = entry(3, TriageLevel.HIGH, base, QueueStatus.WAITING);
        QueueEntry normalLate = entry(4, TriageLevel.NORMAL, base.plusMinutes(5), QueueStatus.WAITING);
        QueueEntry followUp = entry(5, TriageLevel.FOLLOW_UP, base, QueueStatus.WAITING);

        List<QueueEntry> ordered = ordering.orderByEffectivePriority(
                List.of(normalEarly, emergency, high, normalLate, followUp));

        assertThat(ordered).extracting(QueueEntry::getId)
                .containsExactly(2L, 3L, 1L, 4L, 5L);
    }

    @Test
    void orderByEffectivePriority_FifoWithinSameTriageLevel() {
        LocalDateTime base = LocalDateTime.of(2026, 7, 31, 9, 0);
        QueueEntry joinedFirst = entry(1, TriageLevel.NORMAL, base, QueueStatus.WAITING);
        QueueEntry joinedSecond = entry(2, TriageLevel.NORMAL, base.plusMinutes(2), QueueStatus.WAITING);
        QueueEntry joinedThird = entry(3, TriageLevel.NORMAL, base.plusMinutes(4), QueueStatus.WAITING);

        List<QueueEntry> ordered = ordering.orderByEffectivePriority(
                List.of(joinedThird, joinedSecond, joinedFirst));

        assertThat(ordered).extracting(QueueEntry::getId).containsExactly(1L, 2L, 3L);
    }

    @Test
    void orderByEffectivePriority_DoctorOverrideIsFinal() {
        LocalDateTime base = LocalDateTime.of(2026, 7, 31, 9, 0);
        // AI said NORMAL but the doctor overrode to EMERGENCY.
        QueueEntry overridden = entry(1, TriageLevel.NORMAL, base, QueueStatus.WAITING);
        overridden.setDoctorOverrideTriage(TriageLevel.EMERGENCY);
        QueueEntry aiEmergency = entry(2, TriageLevel.EMERGENCY, base.plusMinutes(1), QueueStatus.WAITING);
        QueueEntry normal = entry(3, TriageLevel.NORMAL, base, QueueStatus.WAITING);

        List<QueueEntry> ordered = ordering.orderByEffectivePriority(List.of(normal, aiEmergency, overridden));

        // Overridden entry now outranks the AI-emergency entry and FIFO applies within EMERGENCY.
        assertThat(ordered).extracting(QueueEntry::getId).containsExactly(1L, 2L, 3L);
    }

    @Test
    void orderByEffectivePriority_ExcludesCompletedAndCancelled() {
        LocalDateTime base = LocalDateTime.of(2026, 7, 31, 9, 0);
        QueueEntry waiting = entry(1, TriageLevel.HIGH, base, QueueStatus.WAITING);
        QueueEntry completed = entry(2, TriageLevel.EMERGENCY, base, QueueStatus.COMPLETED);
        QueueEntry cancelled = entry(3, TriageLevel.EMERGENCY, base, QueueStatus.CANCELLED);

        List<QueueEntry> ordered = ordering.orderByEffectivePriority(List.of(waiting, completed, cancelled));

        assertThat(ordered).hasSize(1);
        assertThat(ordered.get(0).getId()).isEqualTo(1L);
    }

    @Test
    void effectiveTriage_PrefersDoctorOverride() {
        QueueEntry e = entry(1, TriageLevel.NORMAL, LocalDateTime.now(), QueueStatus.WAITING);
        assertThat(ordering.effectiveTriage(e)).isEqualTo(TriageLevel.NORMAL);

        e.setDoctorOverrideTriage(TriageLevel.EMERGENCY);
        assertThat(ordering.effectiveTriage(e)).isEqualTo(TriageLevel.EMERGENCY);
    }

    @Test
    void positionOf_ReturnsOneBasedPosition() {
        QueueEntry a = entry(1, TriageLevel.HIGH, LocalDateTime.of(2026, 7, 31, 9, 0), QueueStatus.WAITING);
        QueueEntry b = entry(2, TriageLevel.NORMAL, LocalDateTime.of(2026, 7, 31, 9, 0), QueueStatus.WAITING);
        QueueEntry c = entry(3, TriageLevel.NORMAL, LocalDateTime.of(2026, 7, 31, 9, 5), QueueStatus.WAITING);

        List<QueueEntry> ordered = ordering.orderByEffectivePriority(List.of(a, b, c));

        assertThat(ordering.positionOf(a, ordered)).isEqualTo(1);
        assertThat(ordering.positionOf(b, ordered)).isEqualTo(2);
        assertThat(ordering.positionOf(c, ordered)).isEqualTo(3);
    }

    @Test
    void predictedWaitMinutes_PatientsAheadTimesAvgTime() {
        // Position 3 => 2 patients ahead => 2 x 15 min = 30 min.
        assertThat(ordering.predictedWaitMinutes(3, 15)).isEqualTo(30);
        // Position 1 => next to be seen => 0 min.
        assertThat(ordering.predictedWaitMinutes(1, 15)).isZero();
        // Handles null avg defensively.
        assertThat(ordering.predictedWaitMinutes(3, null)).isZero();
    }
}
