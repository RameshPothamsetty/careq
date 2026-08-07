package com.careq.queue.controller;

import com.careq.queue.dto.AnalyticsSummaryDto;
import com.careq.queue.dto.AutoAssignRequestDto;
import com.careq.queue.dto.AutoAssignResponseDto;
import com.careq.queue.dto.DoctorAnalyticsSummaryDto;
import com.careq.queue.dto.DoctorSuggestionRequestDto;
import com.careq.queue.dto.DoctorSuggestionResponseDto;
import com.careq.queue.dto.JoinQueueRequestDto;
import com.careq.queue.dto.LiveQueueOverviewDto;
import com.careq.queue.dto.OverrideTriageRequestDto;
import com.careq.queue.dto.QueueEntryResponseDto;
import com.careq.queue.dto.QueueStatusResponseDto;
import com.careq.queue.exception.ErrorResponseDto;
import com.careq.queue.exception.RoleGuard;
import com.careq.queue.service.AutoAssignService;
import com.careq.queue.service.DoctorRecommendationService;
import com.careq.queue.service.QueueService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
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
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Queue endpoints (Day 9: fully documented with OpenAPI).
 * Role enforcement uses the X-User-Id / X-User-Role headers propagated by the
 * API Gateway (same convention as Days 3-4). All business logic lives in
 * {@link QueueService}.
 */
@Tag(name = "Queue Management", description = "Patient queue operations, AI symptom triage, AI doctor matching and live " +
        "wait-time prediction. Role rules: PATIENT for join/auto-assign/suggestions/status/history; DOCTOR (own queue) " +
        "or ADMIN for queue actions and analytics.")
@RestController
@RequestMapping("/api/queue")
public class QueueController {

    private final QueueService queueService;
    private final DoctorRecommendationService doctorRecommendationService;
    private final AutoAssignService autoAssignService;

    public QueueController(QueueService queueService,
                           DoctorRecommendationService doctorRecommendationService,
                           AutoAssignService autoAssignService) {
        this.queueService = queueService;
        this.doctorRecommendationService = doctorRecommendationService;
        this.autoAssignService = autoAssignService;
    }

    /** Patient joins the queue for a doctor. Triggers AI triage. Returns entry with predicted wait. */
    @Operation(summary = "Join a doctor's queue (Patient)",
            description = "Adds the calling patient to the given doctor's queue. The free-text symptomText is triaged " +
                    "by the Groq LLM (EMERGENCY > HIGH > NORMAL > FOLLOW_UP); on any AI failure it falls back to NORMAL. " +
                    "The doctor must be available and the patient must not already have an active entry for this doctor.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "201", description = "Joined; entry with live position and predicted wait",
                    content = @Content(schema = @Schema(implementation = QueueEntryResponseDto.class),
                            examples = @ExampleObject(value = "{\"id\":1,\"patientId\":\"550e8400-e29b-41d4-a716-446655440010\",\"patientName\":\"John Patient\",\"doctorCatalogEntryId\":1,\"doctorName\":\"Dr. Arjun Sharma\",\"departmentName\":\"Cardiology\",\"specialization\":\"Interventional Cardiology\",\"symptomText\":\"Severe chest pain radiating to my left arm\",\"aiSuggestedTriage\":\"EMERGENCY\",\"doctorOverrideTriage\":null,\"effectiveTriage\":\"EMERGENCY\",\"status\":\"WAITING\",\"position\":1,\"predictedWaitMinutes\":0,\"joinedAt\":\"2026-07-31T09:00:00\",\"calledAt\":null,\"completedAt\":null}"))),
            @ApiResponse(responseCode = "400", description = "Validation failed",
                    content = @Content(schema = @Schema(implementation = ErrorResponseDto.class))),
            @ApiResponse(responseCode = "403", description = "Caller is not a PATIENT",
                    content = @Content(schema = @Schema(implementation = ErrorResponseDto.class))),
            @ApiResponse(responseCode = "404", description = "Doctor catalog entry not found",
                    content = @Content(schema = @Schema(implementation = ErrorResponseDto.class))),
            @ApiResponse(responseCode = "409", description = "Doctor unavailable, or patient already has an active entry for this doctor",
                    content = @Content(schema = @Schema(implementation = ErrorResponseDto.class))),
            @ApiResponse(responseCode = "503", description = "Doctor service temporarily unavailable",
                    content = @Content(schema = @Schema(implementation = ErrorResponseDto.class)))
    })
    @PostMapping("/join")
    public ResponseEntity<QueueEntryResponseDto> joinQueue(
            @Parameter(hidden = true) @RequestHeader("X-User-Id") String userId,
            @Parameter(hidden = true) @RequestHeader("X-User-Role") String role,
            @Valid @RequestBody JoinQueueRequestDto request) {

        RoleGuard.requireRole(role, "PATIENT");
        return ResponseEntity.status(HttpStatus.CREATED).body(queueService.joinQueue(userId, request));
    }

    /** AI doctor recommendation: symptoms → top ranked available doctors. */
    @Operation(summary = "AI doctor recommendation (Patient)",
            description = "Given free-text symptoms, the Groq LLM assesses urgency AND the most likely department, " +
                    "then this endpoint ranks available doctors by relevance (tie-broken by the shortest live " +
                    "predicted wait for a new joiner). The AI only recommends — the patient confirms a doctor " +
                    "before joining. Degrades gracefully: no GROQ_API_KEY uses keyword matching; no specialty " +
                    "match falls back to the nearest available doctor by wait.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "AI assessment + top-3 ranked doctor suggestions",
                    content = @Content(schema = @Schema(implementation = DoctorSuggestionResponseDto.class),
                            examples = @ExampleObject(value = "{\"triageLevel\":\"EMERGENCY\",\"suggestedDepartment\":\"Cardiology\",\"emergency\":true,\"urgencyNote\":\"EMERGENCY — please seek immediate attention. Nearest available specialists:\",\"suggestions\":[{\"doctorCatalogEntryId\":1,\"name\":\"Dr. Arjun Sharma\",\"departmentName\":\"Cardiology\",\"specialization\":\"Interventional Cardiology\",\"position\":2,\"predictedWaitMinutes\":15,\"matchReason\":\"Best match — Cardiology\"}]}"))),
            @ApiResponse(responseCode = "400", description = "Validation failed (symptomText required)",
                    content = @Content(schema = @Schema(implementation = ErrorResponseDto.class))),
            @ApiResponse(responseCode = "403", description = "Caller is not a PATIENT",
                    content = @Content(schema = @Schema(implementation = ErrorResponseDto.class))),
            @ApiResponse(responseCode = "503", description = "Doctor service temporarily unavailable",
                    content = @Content(schema = @Schema(implementation = ErrorResponseDto.class)))
    })
    @PostMapping("/doctor-suggestions")
    public ResponseEntity<DoctorSuggestionResponseDto> getDoctorSuggestions(
            @Parameter(hidden = true) @RequestHeader("X-User-Role") String role,
            @Valid @RequestBody DoctorSuggestionRequestDto request) {

        RoleGuard.requireRole(role, "PATIENT");
        return ResponseEntity.ok(doctorRecommendationService.recommend(request.getSymptomText()));
    }

    /** Phase 2 — full auto-assignment: symptoms → AI joins the single best doctor (ambiguous → suggestions). */
    @Operation(summary = "Auto-assign to the best doctor (Patient)",
            description = "Given free-text symptoms, the Groq LLM assesses urgency AND the most likely department, the engine " +
                    "ranks available doctors and — when the top match is confident — the patient is joined to that doctor's " +
                    "queue immediately (triage, live position and predicted wait returned, HTTP 201). When the symptoms are " +
                    "ambiguous or no doctor is available, nothing is joined: the top candidates are returned for the patient " +
                    "to confirm manually (HTTP 200, assigned=false). Emergency symptoms are still auto-assigned, with a " +
                    "prominent urgency warning. One LLM call; degrades gracefully: no GROQ_API_KEY uses curated keyword " +
                    "matching.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "201", description = "Assigned — patient joined; entry + assignedDoctor returned",
                    content = @Content(schema = @Schema(implementation = AutoAssignResponseDto.class),
                            examples = @ExampleObject(value = "{\"assigned\":true,\"reason\":\"ASSIGNED\",\"message\":\"You've been matched with Dr. Arjun Sharma (Cardiology) — estimated wait ≈ 15 min\",\"triageLevel\":\"EMERGENCY\",\"suggestedDepartment\":\"Cardiology\",\"emergency\":true,\"urgencyNote\":\"EMERGENCY — please seek immediate attention. Matching you with the nearest available specialist.\",\"assignedDoctor\":{\"doctorCatalogEntryId\":1,\"name\":\"Dr. Arjun Sharma\",\"departmentName\":\"Cardiology\",\"specialization\":\"Interventional Cardiology\",\"position\":2,\"predictedWaitMinutes\":15,\"matchReason\":\"Best match — Cardiology\"},\"entry\":{\"id\":1,\"patientId\":\"550e8400-e29b-41d4-a716-446655440010\",\"doctorCatalogEntryId\":1,\"doctorName\":\"Dr. Arjun Sharma\",\"aiSuggestedTriage\":\"EMERGENCY\",\"status\":\"WAITING\",\"position\":2,\"predictedWaitMinutes\":15},\"suggestions\":[]}"))),
            @ApiResponse(responseCode = "200", description = "Ambiguous symptoms or no available doctors — suggestions returned, nothing joined",
                    content = @Content(schema = @Schema(implementation = AutoAssignResponseDto.class))),
            @ApiResponse(responseCode = "400", description = "Validation failed (symptomText required)",
                    content = @Content(schema = @Schema(implementation = ErrorResponseDto.class))),
            @ApiResponse(responseCode = "403", description = "Caller is not a PATIENT",
                    content = @Content(schema = @Schema(implementation = ErrorResponseDto.class))),
            @ApiResponse(responseCode = "409", description = "Doctor unavailable, or patient already has an active entry for the assigned doctor",
                    content = @Content(schema = @Schema(implementation = ErrorResponseDto.class))),
            @ApiResponse(responseCode = "503", description = "Doctor service temporarily unavailable",
                    content = @Content(schema = @Schema(implementation = ErrorResponseDto.class)))
    })
    @PostMapping("/auto-assign")
    public ResponseEntity<AutoAssignResponseDto> autoAssign(
            @Parameter(hidden = true) @RequestHeader("X-User-Id") String userId,
            @Parameter(hidden = true) @RequestHeader("X-User-Role") String role,
            @Valid @RequestBody AutoAssignRequestDto request) {

        RoleGuard.requireRole(role, "PATIENT");
        AutoAssignResponseDto response = autoAssignService.autoAssign(userId, request);
        return response.isAssigned()
                ? ResponseEntity.status(HttpStatus.CREATED).body(response)
                : ResponseEntity.ok(response);
    }

    /** Patient's own current position + freshly recalculated predicted wait. */
    @Operation(summary = "My queue status (Patient)",
            description = "Returns the calling patient's current active queue entry with a freshly recalculated position " +
                    "and predicted wait (recalculated on every read, never cached). active=false when not in a queue.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Current status (active entry or active=false)",
                    content = @Content(schema = @Schema(implementation = QueueStatusResponseDto.class))),
            @ApiResponse(responseCode = "403", description = "Caller is not a PATIENT",
                    content = @Content(schema = @Schema(implementation = ErrorResponseDto.class)))
    })
    @GetMapping("/my-status")
    public ResponseEntity<QueueStatusResponseDto> getMyStatus(
            @Parameter(hidden = true) @RequestHeader("X-User-Id") String userId,
            @Parameter(hidden = true) @RequestHeader("X-User-Role") String role) {

        RoleGuard.requireRole(role, "PATIENT");
        return ResponseEntity.ok(queueService.getMyStatus(userId));
    }

    /** Patient's recent visit history (completed/cancelled, newest first, capped). */
    @Operation(summary = "My visit history (Patient)",
            description = "Returns the calling patient's recent completed/cancelled visits, newest first. " +
                    "The limit is capped server-side to a max of 50.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "List of past visits (newest first)",
                    content = @Content(schema = @Schema(implementation = QueueEntryResponseDto.class))),
            @ApiResponse(responseCode = "403", description = "Caller is not a PATIENT",
                    content = @Content(schema = @Schema(implementation = ErrorResponseDto.class)))
    })
    @GetMapping("/my-history")
    public ResponseEntity<List<QueueEntryResponseDto>> getMyHistory(
            @Parameter(hidden = true) @RequestHeader("X-User-Id") String userId,
            @Parameter(hidden = true) @RequestHeader("X-User-Role") String role,
            @Parameter(description = "Max entries to return (clamped to 1-50)") @RequestParam(defaultValue = "10") int limit) {

        RoleGuard.requireRole(role, "PATIENT");
        // History grows over time — bound the request server-side regardless of the caller.
        int cappedLimit = Math.min(Math.max(limit, 1), 50);
        return ResponseEntity.ok(queueService.getMyHistory(userId, cappedLimit));
    }

    /** Per-doctor analytics: today's scalars + 7-day trends. Same ownership rule as the live queue. */
    @Operation(summary = "Per-doctor analytics (Doctor/Admin)",
            description = "Today's scalars (patients completed, average wait, average consult time) plus 7-day " +
                    "patient-load and wait-trend series for one doctor. Doctors may only query their own queue; " +
                    "admins may query any.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Analytics summary",
                    content = @Content(schema = @Schema(implementation = DoctorAnalyticsSummaryDto.class))),
            @ApiResponse(responseCode = "403", description = "Wrong role, or a doctor querying another doctor's queue",
                    content = @Content(schema = @Schema(implementation = ErrorResponseDto.class))),
            @ApiResponse(responseCode = "404", description = "Doctor catalog entry not found",
                    content = @Content(schema = @Schema(implementation = ErrorResponseDto.class)))
    })
    @GetMapping("/doctor/{doctorCatalogEntryId}/analytics")
    public ResponseEntity<DoctorAnalyticsSummaryDto> getDoctorAnalytics(
            @Parameter(description = "Doctor catalog entry ID") @PathVariable Long doctorCatalogEntryId,
            @Parameter(hidden = true) @RequestHeader("X-User-Id") String userId,
            @Parameter(hidden = true) @RequestHeader("X-User-Role") String role) {

        RoleGuard.requireAnyRole(role, "DOCTOR", "ADMIN");
        return ResponseEntity.ok(queueService.getDoctorAnalyticsSummary(doctorCatalogEntryId, userId, role));
    }

    /** Doctor/Admin view of a doctor's live queue, ordered by effective triage then FIFO.
     *  Optional {@code search} filters by patient name (case-insensitive substring). */
    @Operation(summary = "Doctor's live queue (Doctor/Admin)",
            description = "Returns a doctor's live queue (WAITING + IN_PROGRESS) ordered by effective triage level then " +
                    "FIFO. Each entry carries a derived position (1 = next to be seen) and predicted wait. " +
                    "Optional search narrows rows by patient name (positions remain the real queue positions). " +
                    "Doctors may only view their own queue; admins may view any.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Live queue entries",
                    content = @Content(schema = @Schema(implementation = QueueEntryResponseDto.class))),
            @ApiResponse(responseCode = "403", description = "Wrong role, or a doctor viewing another doctor's queue",
                    content = @Content(schema = @Schema(implementation = ErrorResponseDto.class))),
            @ApiResponse(responseCode = "404", description = "Doctor catalog entry not found",
                    content = @Content(schema = @Schema(implementation = ErrorResponseDto.class))),
            @ApiResponse(responseCode = "503", description = "Doctor service temporarily unavailable",
                    content = @Content(schema = @Schema(implementation = ErrorResponseDto.class)))
    })
    @GetMapping("/doctor/{doctorCatalogEntryId}")
    public ResponseEntity<List<QueueEntryResponseDto>> getDoctorQueue(
            @Parameter(description = "Doctor catalog entry ID") @PathVariable Long doctorCatalogEntryId,
            @Parameter(description = "Case-insensitive substring match on patient name") @RequestParam(required = false) String search,
            @Parameter(hidden = true) @RequestHeader("X-User-Id") String userId,
            @Parameter(hidden = true) @RequestHeader("X-User-Role") String role) {

        RoleGuard.requireAnyRole(role, "DOCTOR", "ADMIN");
        return ResponseEntity.ok(queueService.getDoctorQueue(doctorCatalogEntryId, search, userId, role));
    }

    /** Doctor sets the final triage override; queue reordering happens on next read. */
    @Operation(summary = "Override triage (Doctor/Admin)",
            description = "Sets the final triage level for a queue entry, overriding the AI suggestion. " +
                    "The override drives queue ordering. Cannot be applied to IN_PROGRESS / COMPLETED / CANCELLED entries.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Entry with the new effectiveTriage",
                    content = @Content(schema = @Schema(implementation = QueueEntryResponseDto.class))),
            @ApiResponse(responseCode = "400", description = "Invalid triage level or entry not in WAITING state",
                    content = @Content(schema = @Schema(implementation = ErrorResponseDto.class))),
            @ApiResponse(responseCode = "403", description = "Wrong role or not the owning doctor",
                    content = @Content(schema = @Schema(implementation = ErrorResponseDto.class))),
            @ApiResponse(responseCode = "404", description = "Queue entry not found",
                    content = @Content(schema = @Schema(implementation = ErrorResponseDto.class)))
    })
    @PutMapping("/{id}/override-triage")
    public ResponseEntity<QueueEntryResponseDto> overrideTriage(
            @Parameter(description = "Queue entry ID") @PathVariable Long id,
            @Parameter(hidden = true) @RequestHeader("X-User-Id") String userId,
            @Parameter(hidden = true) @RequestHeader("X-User-Role") String role,
            @Valid @RequestBody OverrideTriageRequestDto request) {

        RoleGuard.requireAnyRole(role, "DOCTOR", "ADMIN");
        return ResponseEntity.ok(queueService.overrideTriage(id, request, userId, role));
    }

    /** Doctor marks the next patient as IN_PROGRESS. */
    @Operation(summary = "Call next patient (Doctor/Admin)",
            description = "Marks a WAITING entry as IN_PROGRESS (the patient is called). Only valid for WAITING entries.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Entry now IN_PROGRESS with calledAt set",
                    content = @Content(schema = @Schema(implementation = QueueEntryResponseDto.class))),
            @ApiResponse(responseCode = "400", description = "Entry is not in WAITING state",
                    content = @Content(schema = @Schema(implementation = ErrorResponseDto.class))),
            @ApiResponse(responseCode = "403", description = "Wrong role or not the owning doctor",
                    content = @Content(schema = @Schema(implementation = ErrorResponseDto.class))),
            @ApiResponse(responseCode = "404", description = "Queue entry not found",
                    content = @Content(schema = @Schema(implementation = ErrorResponseDto.class)))
    })
    @PutMapping("/{id}/call-next")
    public ResponseEntity<QueueEntryResponseDto> callNext(
            @Parameter(description = "Queue entry ID") @PathVariable Long id,
            @Parameter(hidden = true) @RequestHeader("X-User-Id") String userId,
            @Parameter(hidden = true) @RequestHeader("X-User-Role") String role) {

        RoleGuard.requireAnyRole(role, "DOCTOR", "ADMIN");
        return ResponseEntity.ok(queueService.callNext(id, userId, role));
    }

    /** Patient leaves the queue before being seen (WAITING only); admin can cancel any entry. */
    @Operation(summary = "Cancel a queue entry (Patient/Admin)",
            description = "Cancels a WAITING entry. A patient can cancel their own entry; an admin can cancel any entry. " +
                    "Cancelled visits still appear in the patient's visit history.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Entry now CANCELLED",
                    content = @Content(schema = @Schema(implementation = QueueEntryResponseDto.class))),
            @ApiResponse(responseCode = "400", description = "Entry is not in WAITING state",
                    content = @Content(schema = @Schema(implementation = ErrorResponseDto.class))),
            @ApiResponse(responseCode = "403", description = "Wrong role, or a patient cancelling someone else's entry",
                    content = @Content(schema = @Schema(implementation = ErrorResponseDto.class))),
            @ApiResponse(responseCode = "404", description = "Queue entry not found",
                    content = @Content(schema = @Schema(implementation = ErrorResponseDto.class)))
    })
    @PutMapping("/{id}/cancel")
    public ResponseEntity<QueueEntryResponseDto> cancel(
            @Parameter(description = "Queue entry ID") @PathVariable Long id,
            @Parameter(hidden = true) @RequestHeader("X-User-Id") String userId,
            @Parameter(hidden = true) @RequestHeader("X-User-Role") String role) {

        RoleGuard.requireAnyRole(role, "PATIENT", "ADMIN");
        return ResponseEntity.ok(queueService.cancel(id, userId, role));
    }

    /** Doctor marks a patient COMPLETED. */
    @Operation(summary = "Complete a consultation (Doctor/Admin)",
            description = "Marks an IN_PROGRESS entry as COMPLETED; it leaves the active queue and remaining waits " +
                    "are recalculated on the next read.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Entry now COMPLETED with completedAt set",
                    content = @Content(schema = @Schema(implementation = QueueEntryResponseDto.class))),
            @ApiResponse(responseCode = "400", description = "Entry is not in IN_PROGRESS state",
                    content = @Content(schema = @Schema(implementation = ErrorResponseDto.class))),
            @ApiResponse(responseCode = "403", description = "Wrong role or not the owning doctor",
                    content = @Content(schema = @Schema(implementation = ErrorResponseDto.class))),
            @ApiResponse(responseCode = "404", description = "Queue entry not found",
                    content = @Content(schema = @Schema(implementation = ErrorResponseDto.class)))
    })
    @PutMapping("/{id}/complete")
    public ResponseEntity<QueueEntryResponseDto> complete(
            @Parameter(description = "Queue entry ID") @PathVariable Long id,
            @Parameter(hidden = true) @RequestHeader("X-User-Id") String userId,
            @Parameter(hidden = true) @RequestHeader("X-User-Role") String role) {

        RoleGuard.requireAnyRole(role, "DOCTOR", "ADMIN");
        return ResponseEntity.ok(queueService.complete(id, userId, role));
    }

    /** Admin-only live overview across all doctors. */
    @Operation(summary = "Live queue overview (Admin only)",
            description = "Hospital-wide live overview: summary metrics (waiting, in-progress, doctors online/offline, " +
                    "delayed, average wait) plus a per-doctor breakdown with queue loads.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Live overview",
                    content = @Content(schema = @Schema(implementation = LiveQueueOverviewDto.class),
                            examples = @ExampleObject(value = "{\"totalWaiting\":14,\"totalInProgress\":3,\"doctorsOnline\":8,\"doctorsOffline\":2,\"delayedConsultations\":2,\"averageWaitMinutes\":22,\"doctors\":[{\"doctorCatalogEntryId\":1,\"doctorName\":\"Dr. Arjun Sharma\",\"departmentName\":\"Cardiology\",\"waitingCount\":4,\"inProgressCount\":1,\"delayedCount\":1,\"longestWaitMinutes\":60}]}"))),
            @ApiResponse(responseCode = "403", description = "Caller is not an ADMIN",
                    content = @Content(schema = @Schema(implementation = ErrorResponseDto.class)))
    })
    @GetMapping("/live")
    public ResponseEntity<LiveQueueOverviewDto> getLiveOverview(
            @Parameter(hidden = true) @RequestHeader("X-User-Role") String role) {

        RoleGuard.requireRole(role, "ADMIN");
        return ResponseEntity.ok(queueService.getLiveOverview());
    }

    /** Admin-only analytics summary: patients/day, avg wait trend, department distribution (last 7 days). */
    @Operation(summary = "Analytics summary (Admin only)",
            description = "Last-7-day aggregation: patients handled per day, average wait-time trend, and " +
                    "department distribution. Aggregated in MySQL (GROUP BY DATE), zero-filled so charts always " +
                    "render a complete window.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "7-day analytics summary",
                    content = @Content(schema = @Schema(implementation = AnalyticsSummaryDto.class))),
            @ApiResponse(responseCode = "403", description = "Caller is not an ADMIN",
                    content = @Content(schema = @Schema(implementation = ErrorResponseDto.class)))
    })
    @GetMapping("/analytics/summary")
    public ResponseEntity<AnalyticsSummaryDto> getAnalyticsSummary(
            @Parameter(hidden = true) @RequestHeader("X-User-Role") String role) {

        RoleGuard.requireRole(role, "ADMIN");
        return ResponseEntity.ok(queueService.getAnalyticsSummary());
    }
}
