package com.careq.doctor.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

@Schema(description = "Department create/update payload.")
public class DepartmentRequestDto {

    @Schema(description = "Unique department name", example = "Cardiology", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotBlank(message = "Department name is required")
    @Size(max = 255, message = "Department name must not exceed 255 characters")
    private String name;

    @Schema(description = "Department description", example = "Heart and cardiovascular system")
    @Size(max = 2000, message = "Description must not exceed 2000 characters")
    private String description;

    public DepartmentRequestDto() {
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }
}
