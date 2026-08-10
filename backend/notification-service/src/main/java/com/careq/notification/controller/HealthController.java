package com.careq.notification.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@Tag(name = "Health", description = "Operational health check endpoint.")
@RestController
@RequestMapping("/api/notifications")
public class HealthController {

    @Operation(summary = "Notification service health check",
            description = "Returns the service name, status and a timestamp. Used by load balancers and operators.")
    @ApiResponse(responseCode = "200", description = "Service is up",
            content = @Content(schema = @Schema(example = "{\"service\":\"notification-service\",\"status\":\"UP\",\"timestamp\":1700000000000}")))
    @GetMapping("/health")
    public ResponseEntity<Map<String, Object>> healthCheck() {
        return ResponseEntity.ok(
                Map.of("service", "notification-service", "status", "UP", "timestamp", System.currentTimeMillis())
        );
    }
}
