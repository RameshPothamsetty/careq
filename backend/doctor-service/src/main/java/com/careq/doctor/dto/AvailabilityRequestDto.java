package com.careq.doctor.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;

@Schema(description = "Availability toggle payload.")
public class AvailabilityRequestDto {

    @Schema(description = "Whether the doctor is accepting queue joins", example = "false", requiredMode = Schema.RequiredMode.REQUIRED)
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
