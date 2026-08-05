package com.careq.queue.dto;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * Per-doctor summary card data for the Admin live overview (Day 9: fully documented).
 */
@Schema(description = "Per-doctor queue summary for the Admin live overview.")
public class DoctorQueueStatsDto {

    @Schema(description = "Doctor catalog entry ID", example = "1")
    private Long doctorCatalogEntryId;

    @Schema(description = "Doctor's display name", example = "Dr. Arjun Sharma")
    private String doctorName;

    @Schema(description = "Doctor's user UUID", example = "550e8400-e29b-41d4-a716-446655440001")
    private String doctorUserId;

    @Schema(description = "Department display name", example = "Cardiology")
    private String departmentName;

    @Schema(description = "Specialization", example = "Interventional Cardiology")
    private String specialization;

    @Schema(description = "Average consultation time in minutes", example = "15")
    private Integer avgConsultationTimeMinutes;

    @Schema(description = "Whether the doctor is online", example = "true")
    private Boolean isAvailable;

    @Schema(description = "Patients waiting in this doctor's queue", example = "4")
    private int waitingCount;

    @Schema(description = "Patients currently in consultation", example = "1")
    private int inProgressCount;

    @Schema(description = "Waiting patients exceeding the delay threshold", example = "1")
    private int delayedCount;

    @Schema(description = "Longest predicted wait in this queue (minutes)", example = "60")
    private Integer longestWaitMinutes;

    public DoctorQueueStatsDto() {
    }

    public Long getDoctorCatalogEntryId() {
        return doctorCatalogEntryId;
    }

    public void setDoctorCatalogEntryId(Long doctorCatalogEntryId) {
        this.doctorCatalogEntryId = doctorCatalogEntryId;
    }

    public String getDoctorName() {
        return doctorName;
    }

    public void setDoctorName(String doctorName) {
        this.doctorName = doctorName;
    }

    public String getDoctorUserId() {
        return doctorUserId;
    }

    public void setDoctorUserId(String doctorUserId) {
        this.doctorUserId = doctorUserId;
    }

    public String getDepartmentName() {
        return departmentName;
    }

    public void setDepartmentName(String departmentName) {
        this.departmentName = departmentName;
    }

    public String getSpecialization() {
        return specialization;
    }

    public void setSpecialization(String specialization) {
        this.specialization = specialization;
    }

    public Integer getAvgConsultationTimeMinutes() {
        return avgConsultationTimeMinutes;
    }

    public void setAvgConsultationTimeMinutes(Integer avgConsultationTimeMinutes) {
        this.avgConsultationTimeMinutes = avgConsultationTimeMinutes;
    }

    public Boolean getIsAvailable() {
        return isAvailable;
    }

    public void setIsAvailable(Boolean isAvailable) {
        this.isAvailable = isAvailable;
    }

    public int getWaitingCount() {
        return waitingCount;
    }

    public void setWaitingCount(int waitingCount) {
        this.waitingCount = waitingCount;
    }

    public int getInProgressCount() {
        return inProgressCount;
    }

    public void setInProgressCount(int inProgressCount) {
        this.inProgressCount = inProgressCount;
    }

    public int getDelayedCount() {
        return delayedCount;
    }

    public void setDelayedCount(int delayedCount) {
        this.delayedCount = delayedCount;
    }

    public Integer getLongestWaitMinutes() {
        return longestWaitMinutes;
    }

    public void setLongestWaitMinutes(Integer longestWaitMinutes) {
        this.longestWaitMinutes = longestWaitMinutes;
    }
}
