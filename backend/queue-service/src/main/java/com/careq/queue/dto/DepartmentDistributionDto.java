package com.careq.queue.dto;

import io.swagger.v3.oas.annotations.media.Schema;

/** One department's share of patients handled in the analytics window. */
@Schema(description = "One department's share of patients handled in the analytics window.")
public class DepartmentDistributionDto {

    @Schema(description = "Department display name", example = "Cardiology")
    private String departmentName;

    @Schema(description = "Patients completed in this department during the window", example = "6")
    private long patientCount;

    public DepartmentDistributionDto() {
    }

    public DepartmentDistributionDto(String departmentName, long patientCount) {
        this.departmentName = departmentName;
        this.patientCount = patientCount;
    }

    public String getDepartmentName() {
        return departmentName;
    }

    public void setDepartmentName(String departmentName) {
        this.departmentName = departmentName;
    }

    public long getPatientCount() {
        return patientCount;
    }

    public void setPatientCount(long patientCount) {
        this.patientCount = patientCount;
    }
}
