package com.careq.notification.controller;

import com.careq.notification.dto.NotificationListResponse;
import com.careq.notification.dto.NotificationResponseDto;
import com.careq.notification.exception.ErrorResponseDto;
import com.careq.notification.service.NotificationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Day 13 — persisted notification endpoints.
 * Identity (X-User-Id / X-User-Role) is provided by the API Gateway after JWT
 * validation, so no Bearer token is read inside this service — the same
 * header-based trust pattern as every other CareQ service.
 */
@Tag(name = "Notifications", description = "Persisted in-app notifications. Any authenticated role may list their own; " +
        "marking read is restricted to the recipient (admins may mark any).")
@RestController
@RequestMapping("/api/notifications")
public class NotificationController {

    private final NotificationService notificationService;

    public NotificationController(NotificationService notificationService) {
        this.notificationService = notificationService;
    }

    @Operation(summary = "My notifications (paginated)",
            description = "Returns the calling user's own notifications, newest first, with a total unread count " +
                    "for the badge. Only the caller's own rows are ever returned.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Page of the caller's notifications + unreadCount",
                    content = @Content(schema = @Schema(implementation = NotificationListResponse.class))),
            @ApiResponse(responseCode = "400", description = "Missing X-User-Id header",
                    content = @Content(schema = @Schema(implementation = ErrorResponseDto.class)))
    })
    @GetMapping("/me")
    public ResponseEntity<NotificationListResponse> getMyNotifications(
            @Parameter(hidden = true) @RequestHeader("X-User-Id") String userId,
            @Parameter(description = "Zero-based page number") @RequestParam(defaultValue = "0") int page,
            @Parameter(description = "Page size (clamped to max 50)") @RequestParam(defaultValue = "20") int size) {

        return ResponseEntity.ok(notificationService.getMyNotifications(userId, page, size));
    }

    @Operation(summary = "Mark a notification as read",
            description = "Marks the notification as read. Only the recipient may mark their own; admins may mark any. " +
                    "Idempotent — marking an already-read notification is a no-op.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "The updated notification (read=true)",
                    content = @Content(schema = @Schema(implementation = NotificationResponseDto.class))),
            @ApiResponse(responseCode = "403", description = "Caller is not the recipient and not an admin",
                    content = @Content(schema = @Schema(implementation = ErrorResponseDto.class))),
            @ApiResponse(responseCode = "404", description = "Notification not found",
                    content = @Content(schema = @Schema(implementation = ErrorResponseDto.class)))
    })
    @PutMapping("/{id}/read")
    public ResponseEntity<NotificationResponseDto> markRead(
            @Parameter(description = "Notification ID") @PathVariable Long id,
            @Parameter(hidden = true) @RequestHeader("X-User-Id") String userId,
            @Parameter(hidden = true) @RequestHeader("X-User-Role") String role) {

        return ResponseEntity.ok(notificationService.markRead(id, userId, role));
    }
}
