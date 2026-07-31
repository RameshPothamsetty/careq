package com.careq.queue.dto;

import com.careq.queue.entity.TriageLevel;
import jakarta.validation.constraints.NotNull;

/**
 * Request body for PUT /api/queue/{id}/override-triage.
 * The doctor sets the final triage level, overriding the AI suggestion.
 */
public class OverrideTriageRequestDto {

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
