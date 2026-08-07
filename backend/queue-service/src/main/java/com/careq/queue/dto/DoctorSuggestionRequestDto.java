package com.careq.queue.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Request body for POST /api/queue/doctor-suggestions.
 */
@Schema(description = "AI doctor-recommendation request payload.")
public class DoctorSuggestionRequestDto {

    @Schema(description = "Free-text symptoms — the AI uses them to recommend the best-matching available doctors",
            example = "Persistent headache with blurred vision for two days",
            requiredMode = Schema.RequiredMode.REQUIRED)
    @NotBlank(message = "symptomText is required")
    @Size(max = 2000, message = "symptomText must not exceed 2000 characters")
    private String symptomText;

    public DoctorSuggestionRequestDto() {
    }

    public String getSymptomText() {
        return symptomText;
    }

    public void setSymptomText(String symptomText) {
        this.symptomText = symptomText;
    }
}
