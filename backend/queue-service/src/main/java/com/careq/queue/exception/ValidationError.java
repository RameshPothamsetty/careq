package com.careq.queue.exception;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * A single field-level validation failure (shared error shape).
 */
@Schema(description = "A single field validation failure.")
public class ValidationError {

    @Schema(description = "Name of the invalid field", example = "symptomText")
    private String field;

    @Schema(description = "Validation message for the field", example = "symptomText is required")
    private String message;

    public ValidationError() {
    }

    public ValidationError(String field, String message) {
        this.field = field;
        this.message = message;
    }

    public String getField() {
        return field;
    }

    public void setField(String field) {
        this.field = field;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }
}
