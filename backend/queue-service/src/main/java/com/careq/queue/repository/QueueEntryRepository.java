package com.careq.queue.repository;

import com.careq.queue.entity.QueueEntry;
import com.careq.queue.entity.QueueStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface QueueEntryRepository extends JpaRepository<QueueEntry, Long> {

    Optional<QueueEntry> findFirstByPatientIdAndStatusInOrderByJoinedAtDesc(String patientId, List<QueueStatus> statuses);

    boolean existsByPatientIdAndDoctorCatalogEntryIdAndStatusIn(String patientId, Long doctorCatalogEntryId, List<QueueStatus> statuses);

    List<QueueEntry> findByDoctorCatalogEntryIdAndStatusInOrderByJoinedAtAsc(Long doctorCatalogEntryId, List<QueueStatus> statuses);

    List<QueueEntry> findAllByStatusIn(List<QueueStatus> statuses);

    long countByStatusAndDoctorCatalogEntryId(QueueStatus status, Long doctorCatalogEntryId);
}
