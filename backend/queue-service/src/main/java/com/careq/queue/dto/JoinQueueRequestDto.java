package com.careq.queue.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * Request body for POST /api/queue/join.
 */
public class JoinQueueRequestDto {

    /**
     * Patient's display name, sent by the frontend from the auth session so
     * the doctor's queue view can show real names (added Day 7a). Optional —
     * legacy clients that omit it get null and the UI falls back to an ID.
     */
    @Size(max = 255, message = "patientName must not exceed 255 characters")
    private String patientName;

    @NotNull(message = "doctorCatalogEntryId is required")
    private Long doctorCatalogEntryId;

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
