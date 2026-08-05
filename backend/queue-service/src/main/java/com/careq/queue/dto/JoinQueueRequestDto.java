package com.careq.queue.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * Request body for POST /api/queue/join (Day 9: fully documented).
 */
@Schema(description = "Join-queue request payload.")
public class JoinQueueRequestDto {

    @Schema(description = "Patient's display name (sent from the auth session so the doctor's queue shows real names)", example = "John Patient")
    @Size(max = 255, message = "patientName must not exceed 255 characters")
    private String patientName;

    @Schema(description = "Doctor catalog entry ID to queue behind", example = "1", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotNull(message = "doctorCatalogEntryId is required")
    private Long doctorCatalogEntryId;

    @Schema(description = "Free-text symptoms, triaged by the AI (EMERGENCY > HIGH > NORMAL > FOLLOW_UP)", example = "Severe chest pain radiating to my left arm for the past hour", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotBlank(message = "symptomText is required")
    @Size(max = 2000, message = "symptomText must not exceed 2000 characters")
    private String symptomText;

    public JoinQueueRequestDto() {
    }

    public String getPatientName() {
        return patientName;
    }

    public void setPatientName(String patientName) {
        this.patientName = patientName;
    }

    public Long getDoctorCatalogEntryId() {
        return doctorCatalogEntryId;
    }

    public void setDoctorCatalogEntryId(Long doctorCatalogEntryId) {
        this.doctorCatalogEntryId = doctorCatalogEntryId;
    }

    public String getSymptomText() {
        return symptomText;
    }

    public void setSymptomText(String symptomText) {
        this.symptomText = symptomText;
    }
}
