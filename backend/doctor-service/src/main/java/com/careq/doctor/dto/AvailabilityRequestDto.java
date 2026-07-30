package com.careq.doctor.dto;

import jakarta.validation.constraints.NotNull;

public class AvailabilityRequestDto {

    @NotNull(message = "Availability status is required")
    private Boolean isAvailable;

    public AvailabilityRequestDto() {
    }

    public Boolean getIsAvailable() {
        return isAvailable;
    }

    public void setIsAvailable(Boolean isAvailable) {
        this.isAvailable = isAvailable;
    }
}
