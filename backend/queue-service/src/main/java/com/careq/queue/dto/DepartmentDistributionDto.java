package com.careq.queue.dto;

/** One department's share of patients handled in the analytics window. */
public class DepartmentDistributionDto {

    private String departmentName;
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
