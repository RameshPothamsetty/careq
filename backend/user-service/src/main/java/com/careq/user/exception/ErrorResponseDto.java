package com.careq.user.exception;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Standard error response shape shared by every CareQ service (Day 9):
 * {@code timestamp}, {@code status}, {@code error}, {@code message},
 * {@code path}, and an optional {@code validationErrors} list (field + message
 * pairs) that is only present on 400 validation failures.
 */
@Schema(description = "Standard error response shape shared by all CareQ services.")
public class ErrorResponseDto {

    @Schema(description = "When the error occurred", example = "2026-08-05T10:00:00")
    private LocalDateTime timestamp;

    @Schema(description = "HTTP status code", example = "404")
    private int status;

    @Schema(description = "Short HTTP status reason phrase", example = "Not Found")
    private String error;

    @Schema(description = "Human-readable error message", example = "Profile not found for user: 550e8400-e29b-41d4-a716-446655440000")
    private String message;

    @Schema(description = "Request path that produced the error", example = "/api/users/550e8400-e29b-41d4-a716-446655440000")
    private String path;

    @Schema(description = "Field-level validation failures (only present on 400 validation errors)")
    private List<ValidationError> validationErrors;

    public ErrorResponseDto() {
    }

    public ErrorResponseDto(int status, String error, String message) {
        this.timestamp = LocalDateTime.now();
        this.status = status;
        this.error = error;
        this.message = message;
    }

    public LocalDateTime getTimestamp() {
        return timestamp;
    }

    public void setTimestamp(LocalDateTime timestamp) {
        this.timestamp = timestamp;
    }

    public int getStatus() {
        return status;
    }

    public void setStatus(int status) {
        this.status = status;
    }

    public String getError() {
        return error;
    }

    public void setError(String error) {
        this.error = error;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }

    public String getPath() {
        return path;
    }

    public void setPath(String path) {
        this.path = path;
    }

    public List<ValidationError> getValidationErrors() {
        return validationErrors;
    }

    public void setValidationErrors(List<ValidationError> validationErrors) {
        this.validationErrors = validationErrors;
    }
}
