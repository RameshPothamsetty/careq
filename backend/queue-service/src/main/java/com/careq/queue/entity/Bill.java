package com.careq.queue.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * An invoice for one completed consultation. Created automatically when a
 * queue entry is marked COMPLETED (fee = the doctor's catalog consultation
 * fee at that moment, so the bill is a point-in-time snapshot). One bill per
 * queue entry (unique index).
 *
 * <p>Payment is intentionally a SANDBOX: {@code pay()} flips the status to
 * PAID and records the chosen method — no external gateway is called and no
 * credentials are needed. Swapping in a real gateway later only touches
 * BillService.
 */
@Entity
@Table(name = "bills", indexes = {
        @Index(name = "idx_bill_patient", columnList = "patient_id"),
        @Index(name = "idx_bill_status", columnList = "status")
})
public class Bill {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** Queue entry this bill invoices (one-to-one via unique index). */
    @Column(name = "queue_entry_id", nullable = false, unique = true)
    private Long queueEntryId;

    /** Patient user id (UUID), propagated via X-User-Id header. Plain reference — no FK. */
    @Column(name = "patient_id", length = 36, nullable = false)
    private String patientId;

    @Column(name = "doctor_catalog_entry_id", nullable = false)
    private Long doctorCatalogEntryId;

    @Column(name = "doctor_name", length = 255, nullable = false)
    private String doctorName;

    @Column(name = "department_name", length = 255, nullable = false)
    private String departmentName;

    @Column(name = "amount", precision = 12, scale = 2, nullable = false)
    private BigDecimal amount;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", length = 20, nullable = false)
    private BillStatus status = BillStatus.PENDING;

    /** Sandbox payment method (e.g. "cash", "upi", "card") — recorded, never processed externally. */
    @Column(name = "payment_method", length = 32)
    private String paymentMethod;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "paid_at")
    private LocalDateTime paidAt;

    public Bill() {
    }

    public Bill(Long queueEntryId, String patientId, Long doctorCatalogEntryId,
                String doctorName, String departmentName, BigDecimal amount) {
        this.queueEntryId = queueEntryId;
        this.patientId = patientId;
        this.doctorCatalogEntryId = doctorCatalogEntryId;
        this.doctorName = doctorName;
        this.departmentName = departmentName;
        this.amount = amount;
        this.status = BillStatus.PENDING;
        this.createdAt = LocalDateTime.now();
    }

    @PrePersist
    protected void onCreate() {
        if (createdAt == null) {
            createdAt = LocalDateTime.now();
        }
        if (status == null) {
            status = BillStatus.PENDING;
        }
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Long getQueueEntryId() {
        return queueEntryId;
    }

    public String getPatientId() {
        return patientId;
    }

    public Long getDoctorCatalogEntryId() {
        return doctorCatalogEntryId;
    }

    public String getDoctorName() {
        return doctorName;
    }

    public String getDepartmentName() {
        return departmentName;
    }

    public BigDecimal getAmount() {
        return amount;
    }

    public BillStatus getStatus() {
        return status;
    }

    public void setStatus(BillStatus status) {
        this.status = status;
    }

    public String getPaymentMethod() {
        return paymentMethod;
    }

    public void setPaymentMethod(String paymentMethod) {
        this.paymentMethod = paymentMethod;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }

    public LocalDateTime getPaidAt() {
        return paidAt;
    }

    public void setPaidAt(LocalDateTime paidAt) {
        this.paidAt = paidAt;
    }
}
