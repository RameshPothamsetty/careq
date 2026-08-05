package com.careq.doctor.controller;

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
@RequestMapping("/api/doctors")
public class HealthController {

    @Operation(summary = "Doctor service health check",
            description = "Returns the service name, status and a timestamp. Used by load balancers and operators.")
    @ApiResponse(responseCode = "200", description = "Service is up",
            content = @Content(schema = @Schema(example = "{\"service\":\"doctor-service\",\"status\":\"UP\",\"timestamp\":1700000000000}")))
    @GetMapping("/health")
    public ResponseEntity<Map<String, Object>> healthCheck() {
        return ResponseEntity.ok(
                Map.of("service", "doctor-service", "status", "UP", "timestamp", System.currentTimeMillis())
        );
    }
}
