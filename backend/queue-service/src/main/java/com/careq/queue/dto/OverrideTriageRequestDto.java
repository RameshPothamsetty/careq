package com.careq.queue.dto;

import com.careq.queue.entity.TriageLevel;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;

/**
 * Request body for PUT /api/queue/{id}/override-triage (Day 9: fully documented).
 * The doctor sets the final triage level, overriding the AI suggestion.
 */
@Schema(description = "Triage override payload — the doctor's clinical judgment is final.")
public class OverrideTriageRequestDto {

    @Schema(description = "Final triage level", example = "EMERGENCY",
            allowableValues = {"EMERGENCY", "HIGH", "NORMAL", "FOLLOW_UP"}, requiredMode = Schema.RequiredMode.REQUIRED)
    @NotNull(message = "triageLevel is required")
    private TriageLevel triageLevel;

    public OverrideTriageRequestDto() {
    }

    public TriageLevel getTriageLevel() {
        return triageLevel;
    }

    public void setTriageLevel(TriageLevel triageLevel) {
        this.triageLevel = triageLevel;
    }
}
