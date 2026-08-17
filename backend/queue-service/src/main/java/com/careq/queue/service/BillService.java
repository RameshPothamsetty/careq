package com.careq.queue.service;

import com.careq.queue.dto.BillResponseDto;
import com.careq.queue.dto.DoctorCatalogResponseDto;
import com.careq.queue.entity.Bill;
import com.careq.queue.entity.BillStatus;
import com.careq.queue.entity.QueueEntry;
import com.careq.queue.exception.BillNotFoundException;
import com.careq.queue.exception.UnauthorizedAccessException;
import com.careq.queue.repository.BillRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Billing for completed consultations.
 *
 * <p>Creation is transactional and idempotent per queue entry (unique index
 * on queue_entry_id), so a completed visit can never produce two bills.
 * Payments are a SANDBOX: {@link #pay} records the method and flips the
 * status — no external gateway, no credentials, no cost. A real gateway would
 * only replace this one method.
 */
@Service
public class BillService {

    private static final Logger log = LoggerFactory.getLogger(BillService.class);

    private final BillRepository billRepository;

    public BillService(BillRepository billRepository) {
        this.billRepository = billRepository;
    }

    /**
     * Creates a PENDING bill for a completed queue entry. Amount = the
     * doctor's catalog consultation fee (point-in-time snapshot). No-op when
     * a bill already exists for the entry (idempotent completion).
     */
    @Transactional
    public BillResponseDto createBillForCompletedEntry(QueueEntry entry, DoctorCatalogResponseDto doctor) {
        if (billRepository.findByQueueEntryId(entry.getId()).isPresent()) {
            return null;
        }
        Bill bill = new Bill(
                entry.getId(),
                entry.getPatientId(),
                entry.getDoctorCatalogEntryId(),
                doctor.getName() == null ? "Consultation" : doctor.getName(),
                doctor.getDepartmentName() == null ? "General" : doctor.getDepartmentName(),
                doctor.getConsultationFee() == null ? java.math.BigDecimal.ZERO : doctor.getConsultationFee());
        bill = billRepository.save(bill);
        log.debug("Created bill #{} (₹{}) for completed queue entry {}", bill.getId(), bill.getAmount(), entry.getId());
        return BillResponseDto.fromEntity(bill);
    }

    /** The calling patient's bills, newest first. */
    @Transactional(readOnly = true)
    public List<BillResponseDto> getMyBills(String patientId) {
        return billRepository.findByPatientIdOrderByCreatedAtDesc(patientId).stream()
                .map(BillResponseDto::fromEntity)
                .collect(Collectors.toList());
    }

    /** One of the calling patient's bills (only their own). */
    @Transactional(readOnly = true)
    public BillResponseDto getMyBill(Long id, String patientId) {
        return BillResponseDto.fromEntity(findOwnedBill(id, patientId));
    }

    /**
     * Sandbox payment: marks the bill PAID and records the chosen method.
     * Only the owning patient can pay; an already-paid bill is returned as-is.
     */
    @Transactional
    public BillResponseDto pay(Long id, String patientId, String paymentMethod) {
        Bill bill = findOwnedBill(id, patientId);
        if (bill.getStatus() == BillStatus.PAID) {
            return BillResponseDto.fromEntity(bill);
        }
        bill.setStatus(BillStatus.PAID);
        bill.setPaymentMethod(paymentMethod.trim().toLowerCase());
        bill.setPaidAt(LocalDateTime.now());
        bill = billRepository.save(bill);
        log.info("Bill #{} marked PAID via sandbox '{}'", bill.getId(), bill.getPaymentMethod());
        return BillResponseDto.fromEntity(bill);
    }

    private Bill findOwnedBill(Long id, String patientId) {
        return billRepository.findByIdAndPatientId(id, patientId)
                .orElseThrow(() -> {
                    if (billRepository.existsById(id)) {
                        return new UnauthorizedAccessException("You can only view your own bills");
                    }
                    return new BillNotFoundException("Bill not found with id: " + id);
                });
    }
}
