package com.careq.queue.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * Request body for POST /api/queue/join.
 */
public class JoinQueueRequestDto {

    @NotNull(message = "doctorCatalogEntryId is required")
    private Long doctorCatalogEntryId;

    @NotBlank(message = "symptomText is required")
    @Size(max = 2000, message = "symptomText must not exceed 2000 characters")
    private String symptomText;

    public JoinQueueRequestDto() {
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
