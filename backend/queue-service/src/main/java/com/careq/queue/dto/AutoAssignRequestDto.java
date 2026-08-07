package com.careq.queue.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Request body for POST /api/queue/auto-assign (Phase 2 — full auto-assignment).
 *
 * Symptoms only: the AI assesses urgency, matches the most likely department
 * and joins the calling patient to the single best available doctor's queue.
 */
@Schema(description = "Auto-assign request payload — symptoms only; the AI picks and joins the best doctor.")
public class AutoAssignRequestDto {

    @Schema(description = "Patient's display name (sent from the auth session so the doctor's queue shows real names)", example = "John Patient")
    @Size(max = 255, message = "patientName must not exceed 255 characters")
    private String patientName;

    @Schema(description = "Free-text symptoms — the AI assesses urgency, matches the department and joins the single best available doctor",
            example = "Severe chest pain radiating to my left arm for the past hour",
            requiredMode = Schema.RequiredMode.REQUIRED)
    @NotBlank(message = "symptomText is required")
    @Size(max = 2000, message = "symptomText must not exceed 2000 characters")
    private String symptomText;

    public AutoAssignRequestDto() {
    }

    public String getPatientName() {
        return patientName;
    }

    public void setPatientName(String patientName) {
        this.patientName = patientName;
    }

    public String getSymptomText() {
        return symptomText;
    }

    public void setSymptomText(String symptomText) {
        this.symptomText = symptomText;
    }
}
