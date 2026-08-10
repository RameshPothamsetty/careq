package com.careq.notification.exception;

import io.swagger.v3.oas.annotations.media.Schema;

/** A single field-level validation failure (Day 9 shared error shape). */
@Schema(description = "A single field validation failure.")
public class ValidationError {

    @Schema(description = "Name of the invalid field", example = "size")
    private String field;

    @Schema(description = "Validation message for the field", example = "Page size must not be negative")
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
