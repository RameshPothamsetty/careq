package com.careq.queue.repository;

import com.careq.queue.entity.Bill;
import com.careq.queue.entity.BillStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface BillRepository extends JpaRepository<Bill, Long> {

    List<Bill> findByPatientIdOrderByCreatedAtDesc(String patientId);

    Optional<Bill> findByIdAndPatientId(Long id, String patientId);

    Optional<Bill> findByQueueEntryId(Long queueEntryId);

    long countByPatientIdAndStatus(String patientId, BillStatus status);
}
