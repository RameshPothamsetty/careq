package com.careq.doctor.dto;

import com.careq.doctor.entity.Department;
import io.swagger.v3.oas.annotations.media.Schema;

import java.io.Serializable;

@Schema(description = "A hospital department.")
public class DepartmentResponseDto implements Serializable {

    private static final long serialVersionUID = 1L;

    @Schema(description = "Department ID", example = "1")
    private Long id;

    @Schema(description = "Unique department name", example = "Cardiology")
    private String name;

    @Schema(description = "Department description", example = "Heart and cardiovascular system")
    private String description;

    @Schema(description = "Whether the department is active", example = "true")
    private Boolean isActive;

    public DepartmentResponseDto() {
    }

    public static DepartmentResponseDto fromEntity(Department department) {
        DepartmentResponseDto dto = new DepartmentResponseDto();
        dto.setId(department.getId());
        dto.setName(department.getName());
        dto.setDescription(department.getDescription());
        dto.setIsActive(department.getIsActive());
        return dto;
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
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

    public Boolean getIsActive() {
        return isActive;
    }

    public void setIsActive(Boolean isActive) {
        this.isActive = isActive;
    }
}
