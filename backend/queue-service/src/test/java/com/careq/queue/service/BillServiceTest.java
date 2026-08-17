package com.careq.queue.service;

import com.careq.queue.dto.BillResponseDto;
import com.careq.queue.dto.DoctorCatalogResponseDto;
import com.careq.queue.entity.Bill;
import com.careq.queue.entity.BillStatus;
import com.careq.queue.entity.QueueEntry;
import com.careq.queue.exception.BillNotFoundException;
import com.careq.queue.exception.UnauthorizedAccessException;
import com.careq.queue.repository.BillRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Billing — bills are raised once per completed entry at the doctor's catalog
 * fee, patients only see/pay their own, and the sandbox pay marks PAID with
 * the chosen method.
 */
@ExtendWith(MockitoExtension.class)
class BillServiceTest {

    private static final String PATIENT = "patient-uuid";
    private static final String OTHER_PATIENT = "other-uuid";

    @Mock
    private BillRepository billRepository;

    private BillService billService;

    @BeforeEach
    void setUp() {
        billService = new BillService(billRepository);
    }

    private QueueEntry completedEntry() {
        QueueEntry entry = new QueueEntry();
        entry.setId(42L);
        entry.setPatientId(PATIENT);
        entry.setDoctorCatalogEntryId(10L);
        entry.setCompletedAt(LocalDateTime.now());
        return entry;
    }

    private DoctorCatalogResponseDto doctor(BigDecimal fee) {
        DoctorCatalogResponseDto d = new DoctorCatalogResponseDto();
        d.setId(10L);
        d.setName("Dr. Arjun Sharma");
        d.setDepartmentName("Cardiology");
        d.setConsultationFee(fee);
        return d;
    }

    private Bill bill(Long id, String patientId) {
        Bill bill = new Bill(42L, patientId, 10L, "Dr. Arjun Sharma", "Cardiology", new BigDecimal("800.00"));
        bill.setId(id);
        bill.setCreatedAt(LocalDateTime.now());
        return bill;
    }

    @Test
    void createBillForCompletedEntry_SnapshotsDoctorFee() {
        when(billRepository.findByQueueEntryId(42L)).thenReturn(Optional.empty());
        when(billRepository.save(any(Bill.class))).thenAnswer(inv -> inv.getArgument(0));

        BillResponseDto dto = billService.createBillForCompletedEntry(completedEntry(), doctor(new BigDecimal("800.00")));

        ArgumentCaptor<Bill> captor = ArgumentCaptor.forClass(Bill.class);
        verify(billRepository).save(captor.capture());
        Bill saved = captor.getValue();
        assertThat(saved.getQueueEntryId()).isEqualTo(42L);
        assertThat(saved.getPatientId()).isEqualTo(PATIENT);
        assertThat(saved.getAmount()).isEqualByComparingTo("800.00");
        assertThat(saved.getStatus()).isEqualTo(BillStatus.PENDING);
        assertThat(dto.getDoctorName()).isEqualTo("Dr. Arjun Sharma");
    }

    @Test
    void createBillForCompletedEntry_AlreadyBilled_IsNoOp() {
        when(billRepository.findByQueueEntryId(42L)).thenReturn(Optional.of(bill(1L, PATIENT)));

        BillResponseDto dto = billService.createBillForCompletedEntry(completedEntry(), doctor(new BigDecimal("800.00")));

        assertThat(dto).isNull();
        verify(billRepository, never()).save(any());
    }

    @Test
    void getMyBills_ReturnsOwnBillsNewestFirst() {
        when(billRepository.findByPatientIdOrderByCreatedAtDesc(PATIENT))
                .thenReturn(List.of(bill(2L, PATIENT), bill(1L, PATIENT)));

        List<BillResponseDto> bills = billService.getMyBills(PATIENT);

        assertThat(bills).hasSize(2);
        assertThat(bills.get(0).getId()).isEqualTo(2L);
    }

    @Test
    void getMyBill_OtherPatientsBill_Forbidden() {
        when(billRepository.findByIdAndPatientId(1L, OTHER_PATIENT)).thenReturn(Optional.empty());
        when(billRepository.existsById(1L)).thenReturn(true);

        assertThatThrownBy(() -> billService.getMyBill(1L, OTHER_PATIENT))
                .isInstanceOf(UnauthorizedAccessException.class);
    }

    @Test
    void getMyBill_UnknownBill_NotFound() {
        when(billRepository.findByIdAndPatientId(99L, PATIENT)).thenReturn(Optional.empty());
        when(billRepository.existsById(99L)).thenReturn(false);

        assertThatThrownBy(() -> billService.getMyBill(99L, PATIENT))
                .isInstanceOf(BillNotFoundException.class);
    }

    @Test
    void pay_MarksPaidWithMethod() {
        Bill pending = bill(1L, PATIENT);
        when(billRepository.findByIdAndPatientId(1L, PATIENT)).thenReturn(Optional.of(pending));
        when(billRepository.save(any(Bill.class))).thenAnswer(inv -> inv.getArgument(0));

        BillResponseDto dto = billService.pay(1L, PATIENT, " UPI ");

        assertThat(dto.getStatus()).isEqualTo("PAID");
        assertThat(dto.getPaymentMethod()).isEqualTo("upi"); // trimmed + lowercased
        assertThat(pending.getPaidAt()).isNotNull();
    }

    @Test
    void pay_AlreadyPaid_IsNoOp() {
        Bill paid = bill(1L, PATIENT);
        paid.setStatus(BillStatus.PAID);
        paid.setPaymentMethod("cash");
        paid.setPaidAt(LocalDateTime.now());
        when(billRepository.findByIdAndPatientId(1L, PATIENT)).thenReturn(Optional.of(paid));

        BillResponseDto dto = billService.pay(1L, PATIENT, "upi");

        assertThat(dto.getStatus()).isEqualTo("PAID");
        assertThat(dto.getPaymentMethod()).isEqualTo("cash");
        verify(billRepository, never()).save(any());
    }
}
