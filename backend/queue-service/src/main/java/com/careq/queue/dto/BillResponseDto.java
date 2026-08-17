package com.careq.queue.dto;

import com.careq.queue.entity.Bill;
import io.swagger.v3.oas.annotations.media.Schema;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Schema(description = "An invoice for a completed consultation.")
public class BillResponseDto {

    @Schema(description = "Bill ID", example = "1")
    private Long id;

    @Schema(description = "Queue entry this bill invoices", example = "42")
    private Long queueEntryId;

    @Schema(description = "Doctor who conducted the consultation", example = "Dr. Arjun Sharma")
    private String doctorName;

    @Schema(description = "Department of the consultation", example = "Cardiology")
    private String departmentName;

    @Schema(description = "Amount in INR", example = "800.00")
    private BigDecimal amount;

    @Schema(description = "PENDING until paid, then PAID", example = "PENDING")
    private String status;

    @Schema(description = "Sandbox payment method once paid (cash/upi/card)", example = "upi")
    private String paymentMethod;

    @Schema(description = "When the bill was created", example = "2026-08-17T09:00:00")
    private LocalDateTime createdAt;

    @Schema(description = "When the bill was paid (null while PENDING)", example = "2026-08-17T09:05:00")
    private LocalDateTime paidAt;

    public BillResponseDto() {
    }

    public static BillResponseDto fromEntity(Bill bill) {
        BillResponseDto dto = new BillResponseDto();
        dto.setId(bill.getId());
        dto.setQueueEntryId(bill.getQueueEntryId());
        dto.setDoctorName(bill.getDoctorName());
        dto.setDepartmentName(bill.getDepartmentName());
        dto.setAmount(bill.getAmount());
        dto.setStatus(bill.getStatus().name());
        dto.setPaymentMethod(bill.getPaymentMethod());
        dto.setCreatedAt(bill.getCreatedAt());
        dto.setPaidAt(bill.getPaidAt());
        return dto;
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

    public void setQueueEntryId(Long queueEntryId) {
        this.queueEntryId = queueEntryId;
    }

    public String getDoctorName() {
        return doctorName;
    }

    public void setDoctorName(String doctorName) {
        this.doctorName = doctorName;
    }

    public String getDepartmentName() {
        return departmentName;
    }

    public void setDepartmentName(String departmentName) {
        this.departmentName = departmentName;
    }

    public BigDecimal getAmount() {
        return amount;
    }

    public void setAmount(BigDecimal amount) {
        this.amount = amount;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
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
