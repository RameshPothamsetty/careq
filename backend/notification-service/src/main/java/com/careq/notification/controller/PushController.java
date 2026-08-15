package com.careq.notification.controller;

import com.careq.notification.dto.NotificationPreferenceResponseDto;
import com.careq.notification.dto.PushSubscriptionRequestDto;
import com.careq.notification.dto.UpdateNotificationPreferenceRequestDto;
import com.careq.notification.exception.ErrorResponseDto;
import com.careq.notification.service.PushPreferenceService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Web Push subscriptions + per-user delivery preferences.
 *
 * <p>Identity (X-User-Id) comes from the API Gateway after JWT validation, the
 * same header-based trust pattern as every other CareQ endpoint. All four
 * routes sit under the existing {@code /api/notifications/**} gateway route,
 * so no gateway change was needed.
 */
@Tag(name = "Notification Delivery", description = "Web Push subscriptions and per-user delivery preferences .")
@RestController
@RequestMapping("/api/notifications")
public class PushController {

    private final PushPreferenceService pushPreferenceService;

    public PushController(PushPreferenceService pushPreferenceService) {
        this.pushPreferenceService = pushPreferenceService;
    }

    @Operation(summary = "My delivery preferences",
            description = "Returns the caller's delivery preferences. webPushEnabled defaults to true when no "
                    + "preference row exists yet — delivery also requires the browser permission + a subscription.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "The caller's preferences",
                    content = @Content(schema = @Schema(implementation = NotificationPreferenceResponseDto.class))),
            @ApiResponse(responseCode = "400", description = "Missing X-User-Id header",
                    content = @Content(schema = @Schema(implementation = ErrorResponseDto.class)))
    })
    @GetMapping("/preferences")
    public ResponseEntity<NotificationPreferenceResponseDto> getPreferences(
            @Parameter(hidden = true) @RequestHeader("X-User-Id") String userId) {
        return ResponseEntity.ok(pushPreferenceService.getPreferences(userId));
    }

    @Operation(summary = "Update my delivery preferences",
            description = "Upserts the caller's preference row (webPushEnabled is the user's opt-out switch).")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "The updated preferences",
                    content = @Content(schema = @Schema(implementation = NotificationPreferenceResponseDto.class))),
            @ApiResponse(responseCode = "400", description = "Validation failed or missing X-User-Id header",
                    content = @Content(schema = @Schema(implementation = ErrorResponseDto.class)))
    })
    @PutMapping("/preferences")
    public ResponseEntity<NotificationPreferenceResponseDto> updatePreferences(
            @Parameter(hidden = true) @RequestHeader("X-User-Id") String userId,
            @Valid @RequestBody UpdateNotificationPreferenceRequestDto request) {
        return ResponseEntity.ok(pushPreferenceService.updatePreferences(
                userId, Boolean.TRUE.equals(request.getWebPushEnabled())));
    }

    @Operation(summary = "Register a push subscription",
            description = "Registers (or re-registers, upsert by endpoint) the browser's Web Push subscription for "
                    + "the calling user. Called by the SPA after the user grants the Notification permission.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "201", description = "Subscription registered"),
            @ApiResponse(responseCode = "400", description = "Validation failed or missing X-User-Id header",
                    content = @Content(schema = @Schema(implementation = ErrorResponseDto.class)))
    })
    @PostMapping("/push/subscriptions")
    public ResponseEntity<Void> registerSubscription(
            @Parameter(hidden = true) @RequestHeader("X-User-Id") String userId,
            @Valid @RequestBody PushSubscriptionRequestDto request) {
        pushPreferenceService.registerSubscription(userId, request);
        return ResponseEntity.status(HttpStatus.CREATED).build();
    }

    @Operation(summary = "Remove a push subscription",
            description = "Removes the caller's subscription for the given endpoint (e.g. on unsubscribe).")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "204", description = "Subscription removed (idempotent)"),
            @ApiResponse(responseCode = "400", description = "Missing X-User-Id header",
                    content = @Content(schema = @Schema(implementation = ErrorResponseDto.class)))
    })
    @DeleteMapping("/push/subscriptions")
    public ResponseEntity<Void> unregisterSubscription(
            @Parameter(hidden = true) @RequestHeader("X-User-Id") String userId,
            @Parameter(description = "The push endpoint to remove") @RequestParam("endpoint") String endpoint) {
        pushPreferenceService.unregisterSubscription(userId, endpoint);
        return ResponseEntity.noContent().build();
    }
}
