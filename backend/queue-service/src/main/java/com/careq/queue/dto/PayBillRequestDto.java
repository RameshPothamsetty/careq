package com.careq.queue.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

@Schema(description = "Payment request for a pending bill (sandbox — no external gateway).")
public class PayBillRequestDto {

    @Schema(description = "Payment method label: cash | upi | card", example = "upi")
    @NotBlank(message = "paymentMethod is required")
    @Size(max = 32, message = "paymentMethod must be at most 32 characters")
    private String paymentMethod;

    public PayBillRequestDto() {
    }

    public String getPaymentMethod() {
        return paymentMethod;
    }

    public void setPaymentMethod(String paymentMethod) {
        this.paymentMethod = paymentMethod;
    }
}
