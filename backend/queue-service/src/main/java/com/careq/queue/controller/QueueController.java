package com.careq.queue.controller;

import com.careq.queue.dto.JoinQueueRequestDto;
import com.careq.queue.dto.LiveQueueOverviewDto;
import com.careq.queue.dto.OverrideTriageRequestDto;
import com.careq.queue.dto.QueueEntryResponseDto;
import com.careq.queue.dto.QueueStatusResponseDto;
import com.careq.queue.service.QueueService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Queue endpoints. Role enforcement uses the X-User-Id / X-User-Role
 * headers propagated by the API Gateway (same convention as Days 3-4).
 * All business logic lives in {@link QueueService}.
 */
@RestController
@RequestMapping("/api/queue")
public class QueueController {

    private final QueueService queueService;

    public QueueController(QueueService queueService) {
        this.queueService = queueService;
    }

    /** Patient joins the queue for a doctor. Triggers AI triage. Returns entry with predicted wait. */
    @PostMapping("/join")
    public ResponseEntity<QueueEntryResponseDto> joinQueue(
            @RequestHeader("X-User-Id") String userId,
            @RequestHeader("X-User-Role") String role,
            @Valid @RequestBody JoinQueueRequestDto request) {

        if (!"PATIENT".equalsIgnoreCase(role)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }
        return ResponseEntity.status(HttpStatus.CREATED).body(queueService.joinQueue(userId, request));
    }

    /** Patient's own current position + freshly recalculated predicted wait. */
    @GetMapping("/my-status")
    public ResponseEntity<QueueStatusResponseDto> getMyStatus(
            @RequestHeader("X-User-Id") String userId,
            @RequestHeader("X-User-Role") String role) {

        if (!"PATIENT".equalsIgnoreCase(role)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }
        return ResponseEntity.ok(queueService.getMyStatus(userId));
    }

    /** Doctor/Admin view of a doctor's live queue, ordered by effective triage then FIFO. */
    @GetMapping("/doctor/{doctorCatalogEntryId}")
    public ResponseEntity<List<QueueEntryResponseDto>> getDoctorQueue(
            @PathVariable Long doctorCatalogEntryId,
            @RequestHeader("X-User-Id") String userId,
            @RequestHeader("X-User-Role") String role) {

        if (!("DOCTOR".equalsIgnoreCase(role) || "ADMIN".equalsIgnoreCase(role))) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }
        return ResponseEntity.ok(queueService.getDoctorQueue(doctorCatalogEntryId, userId, role));
    }

    /** Doctor sets the final triage override; queue reordering happens on next read. */
    @PutMapping("/{id}/override-triage")
    public ResponseEntity<QueueEntryResponseDto> overrideTriage(
            @PathVariable Long id,
            @RequestHeader("X-User-Id") String userId,
            @RequestHeader("X-User-Role") String role,
            @Valid @RequestBody OverrideTriageRequestDto request) {

        if (!("DOCTOR".equalsIgnoreCase(role) || "ADMIN".equalsIgnoreCase(role))) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }
        return ResponseEntity.ok(queueService.overrideTriage(id, request, userId, role));
    }

    /** Doctor marks the next patient as IN_PROGRESS. */
    @PutMapping("/{id}/call-next")
    public ResponseEntity<QueueEntryResponseDto> callNext(
            @PathVariable Long id,
            @RequestHeader("X-User-Id") String userId,
            @RequestHeader("X-User-Role") String role) {

        if (!("DOCTOR".equalsIgnoreCase(role) || "ADMIN".equalsIgnoreCase(role))) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }
        return ResponseEntity.ok(queueService.callNext(id, userId, role));
    }

    /** Doctor marks a patient COMPLETED. */
    @PutMapping("/{id}/complete")
    public ResponseEntity<QueueEntryResponseDto> complete(
            @PathVariable Long id,
            @RequestHeader("X-User-Id") String userId,
            @RequestHeader("X-User-Role") String role) {

        if (!("DOCTOR".equalsIgnoreCase(role) || "ADMIN".equalsIgnoreCase(role))) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }
        return ResponseEntity.ok(queueService.complete(id, userId, role));
    }

    /** Admin-only live overview across all doctors. */
    @GetMapping("/live")
    public ResponseEntity<LiveQueueOverviewDto> getLiveOverview(
            @RequestHeader("X-User-Role") String role) {

        if (!"ADMIN".equalsIgnoreCase(role)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }
        return ResponseEntity.ok(queueService.getLiveOverview());
    }
}
