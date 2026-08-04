package com.careq.queue.repository;

import com.careq.queue.entity.QueueEntry;
import com.careq.queue.entity.QueueStatus;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface QueueEntryRepository extends JpaRepository<QueueEntry, Long> {

    Optional<QueueEntry> findFirstByPatientIdAndStatusInOrderByJoinedAtDesc(String patientId, List<QueueStatus> statuses);

    boolean existsByPatientIdAndDoctorCatalogEntryIdAndStatusIn(String patientId, Long doctorCatalogEntryId, List<QueueStatus> statuses);

    List<QueueEntry> findByDoctorCatalogEntryIdAndStatusInOrderByJoinedAtAsc(Long doctorCatalogEntryId, List<QueueStatus> statuses);

    List<QueueEntry> findAllByStatusIn(List<QueueStatus> statuses);

    long countByStatusAndDoctorCatalogEntryId(QueueStatus status, Long doctorCatalogEntryId);

    /**
     * A patient's visit history: completed/cancelled entries, newest first.
     * Bounded by the {@link Pageable} (limit) — history grows, so this must
     * never be an unbounded query.
     */
    @Query("SELECT e FROM QueueEntry e WHERE e.patientId = :patientId " +
            "AND e.status IN :statuses ORDER BY e.joinedAt DESC")
    List<QueueEntry> findHistoryByPatientId(@Param("patientId") String patientId,
                                            @Param("statuses") List<QueueStatus> statuses,
                                            Pageable pageable);

    // ── Analytics (Day 7b) — native MySQL aggregation, never row-by-row in Java ──

    /** Patients handled per day = queue entries completed that day. */
    @Query(value = "SELECT DATE_FORMAT(completed_at, '%Y-%m-%d') AS day, COUNT(*) AS cnt " +
            "FROM queue_entries WHERE completed_at IS NOT NULL AND completed_at >= :since " +
            "GROUP BY DATE_FORMAT(completed_at, '%Y-%m-%d')", nativeQuery = true)
    List<DayCountProjection> countCompletedPerDaySince(@Param("since") LocalDateTime since);

    /** Average wait per day = AVG(called_at - joined_at) for entries called that day. */
    @Query(value = "SELECT DATE_FORMAT(called_at, '%Y-%m-%d') AS day, " +
            "AVG(TIMESTAMPDIFF(MINUTE, joined_at, called_at)) AS avgWait " +
            "FROM queue_entries WHERE called_at IS NOT NULL AND called_at >= :since " +
            "GROUP BY DATE_FORMAT(called_at, '%Y-%m-%d')", nativeQuery = true)
    List<DayAvgWaitProjection> avgCalledWaitPerDaySince(@Param("since") LocalDateTime since);

    /** Per-doctor completion counts, joined to department names via the Feign catalog. */
    @Query(value = "SELECT doctor_catalog_entry_id AS doctorCatalogEntryId, COUNT(*) AS cnt " +
            "FROM queue_entries WHERE completed_at IS NOT NULL AND completed_at >= :since " +
            "GROUP BY doctor_catalog_entry_id", nativeQuery = true)
    List<DoctorCountProjection> countCompletedPerDoctorSince(@Param("since") LocalDateTime since);

    // ── Per-doctor analytics (dashboard upgrade) — same aggregation discipline ──

    /** Entries this doctor completed since the given timestamp. */
    @Query(value = "SELECT COUNT(*) FROM queue_entries " +
            "WHERE doctor_catalog_entry_id = :doctorCatalogEntryId " +
            "AND completed_at IS NOT NULL AND completed_at >= :since", nativeQuery = true)
    long countCompletedSince(@Param("doctorCatalogEntryId") Long doctorCatalogEntryId,
                             @Param("since") LocalDateTime since);

    /** Average patient wait (called_at - joined_at, minutes) for entries called since the timestamp. */
    @Query(value = "SELECT AVG(TIMESTAMPDIFF(MINUTE, joined_at, called_at)) FROM queue_entries " +
            "WHERE doctor_catalog_entry_id = :doctorCatalogEntryId " +
            "AND called_at IS NOT NULL AND called_at >= :since", nativeQuery = true)
    Double avgCalledWaitSince(@Param("doctorCatalogEntryId") Long doctorCatalogEntryId,
                              @Param("since") LocalDateTime since);

    /** Average consultation duration (completed_at - called_at, minutes) for completions since the timestamp. */
    @Query(value = "SELECT AVG(TIMESTAMPDIFF(MINUTE, called_at, completed_at)) FROM queue_entries " +
            "WHERE doctor_catalog_entry_id = :doctorCatalogEntryId " +
            "AND completed_at IS NOT NULL AND called_at IS NOT NULL AND completed_at >= :since", nativeQuery = true)
    Double avgConsultDurationSince(@Param("doctorCatalogEntryId") Long doctorCatalogEntryId,
                                   @Param("since") LocalDateTime since);

    /** Per-day completion counts for one doctor (calendar day + count). */
    @Query(value = "SELECT DATE_FORMAT(completed_at, '%Y-%m-%d') AS day, COUNT(*) AS cnt " +
            "FROM queue_entries WHERE doctor_catalog_entry_id = :doctorCatalogEntryId " +
            "AND completed_at IS NOT NULL AND completed_at >= :since " +
            "GROUP BY DATE_FORMAT(completed_at, '%Y-%m-%d')", nativeQuery = true)
    List<DayCountProjection> countCompletedPerDaySinceForDoctor(@Param("doctorCatalogEntryId") Long doctorCatalogEntryId,
                                                               @Param("since") LocalDateTime since);

    /** Per-day average wait for one doctor (calendar day + avg minutes). */
    @Query(value = "SELECT DATE_FORMAT(called_at, '%Y-%m-%d') AS day, " +
            "AVG(TIMESTAMPDIFF(MINUTE, joined_at, called_at)) AS avgWait " +
            "FROM queue_entries WHERE doctor_catalog_entry_id = :doctorCatalogEntryId " +
            "AND called_at IS NOT NULL AND called_at >= :since " +
            "GROUP BY DATE_FORMAT(called_at, '%Y-%m-%d')", nativeQuery = true)
    List<DayAvgWaitProjection> avgCalledWaitPerDaySinceForDoctor(@Param("doctorCatalogEntryId") Long doctorCatalogEntryId,
                                                                @Param("since") LocalDateTime since);
}
