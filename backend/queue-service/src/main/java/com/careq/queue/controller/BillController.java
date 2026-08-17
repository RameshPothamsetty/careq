package com.careq.queue.controller;

import com.careq.queue.dto.BillResponseDto;
import com.careq.queue.dto.PayBillRequestDto;
import com.careq.queue.exception.ErrorResponseDto;
import com.careq.queue.exception.RoleGuard;
import com.careq.queue.service.BillService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Patient billing endpoints. Bills are created automatically when a
 * consultation is completed; patients list/pay their own. Payments are a
 * SANDBOX (no external gateway — see BillService).
 */
@Tag(name = "Billing", description = "Patient bills for completed consultations. Role: PATIENT. " +
        "Payment is a sandbox — it records the method and marks the bill paid, no gateway involved.")
@RestController
@RequestMapping("/api/queue/bills")
public class BillController {

    private final BillService billService;

    public BillController(BillService billService) {
        this.billService = billService;
    }

    @Operation(summary = "My bills (Patient)",
            description = "All of the calling patient's bills, newest first. Bills are created " +
                    "automatically when a consultation completes, at the doctor's catalog fee.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "The patient's bills (may be empty)",
                    content = @Content(schema = @Schema(implementation = BillResponseDto.class))),
            @ApiResponse(responseCode = "403", description = "Caller is not a PATIENT",
                    content = @Content(schema = @Schema(implementation = ErrorResponseDto.class)))
    })
    @GetMapping("/my")
    public ResponseEntity<List<BillResponseDto>> getMyBills(
            @Parameter(hidden = true) @RequestHeader("X-User-Id") String userId,
            @Parameter(hidden = true) @RequestHeader("X-User-Role") String role) {
        RoleGuard.requireRole(role, "PATIENT");
        return ResponseEntity.ok(billService.getMyBills(userId));
    }

    @Operation(summary = "Bill detail (Patient)",
            description = "One of the calling patient's bills.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "The bill",
                    content = @Content(schema = @Schema(implementation = BillResponseDto.class))),
            @ApiResponse(responseCode = "403", description = "Not a patient, or another patient's bill",
                    content = @Content(schema = @Schema(implementation = ErrorResponseDto.class))),
            @ApiResponse(responseCode = "404", description = "Bill not found",
                    content = @Content(schema = @Schema(implementation = ErrorResponseDto.class)))
    })
    @GetMapping("/{id}")
    public ResponseEntity<BillResponseDto> getMyBill(
            @Parameter(description = "Bill ID") @PathVariable Long id,
            @Parameter(hidden = true) @RequestHeader("X-User-Id") String userId,
            @Parameter(hidden = true) @RequestHeader("X-User-Role") String role) {
        RoleGuard.requireRole(role, "PATIENT");
        return ResponseEntity.ok(billService.getMyBill(id, userId));
    }

    @Operation(summary = "Pay a bill (Patient, sandbox)",
            description = "Marks a PENDING bill as PAID and records the payment method. " +
                    "Sandbox: no external gateway is called. Paying an already-paid bill is a no-op.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "The bill, now PAID",
                    content = @Content(schema = @Schema(implementation = BillResponseDto.class))),
            @ApiResponse(responseCode = "400", description = "Validation failed (paymentMethod required)",
                    content = @Content(schema = @Schema(implementation = ErrorResponseDto.class))),
            @ApiResponse(responseCode = "403", description = "Not a patient, or another patient's bill",
                    content = @Content(schema = @Schema(implementation = ErrorResponseDto.class))),
            @ApiResponse(responseCode = "404", description = "Bill not found",
                    content = @Content(schema = @Schema(implementation = ErrorResponseDto.class)))
    })
    @PostMapping("/{id}/pay")
    public ResponseEntity<BillResponseDto> payBill(
            @Parameter(description = "Bill ID") @PathVariable Long id,
            @Parameter(hidden = true) @RequestHeader("X-User-Id") String userId,
            @Parameter(hidden = true) @RequestHeader("X-User-Role") String role,
            @Valid @RequestBody PayBillRequestDto request) {
        RoleGuard.requireRole(role, "PATIENT");
        return ResponseEntity.ok(billService.pay(id, userId, request.getPaymentMethod()));
    }
}
