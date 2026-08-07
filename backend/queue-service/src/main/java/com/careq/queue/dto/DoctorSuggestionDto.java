package com.careq.queue.dto;

import java.math.BigDecimal;

/**
 * One ranked doctor in an AI doctor-recommendation response.
 *
 * Carries the live catalog details plus the queue position and predicted wait
 * a NEW patient would face if they joined this doctor right now (computed from
 * the doctor's current active queue length), and why the AI matched them.
 */
public class DoctorSuggestionDto {

    private Long doctorCatalogEntryId;
    private String name;
    private String departmentName;
    private String specialization;
    private String qualification;
    private Integer experienceYears;
    private BigDecimal consultationFee;
    private Integer avgConsultationTimeMinutes;
    private Boolean isAvailable;

    /** Queue position a new joiner would get (current active count + 1). */
    private Integer position;

    /** Predicted wait in minutes a new joiner would face right now. */
    private Integer predictedWaitMinutes;

    /** Human-readable reason this doctor was matched. */
    private String matchReason;

    public DoctorSuggestionDto() {
    }

    public Long getDoctorCatalogEntryId() {
        return doctorCatalogEntryId;
    }

    public void setDoctorCatalogEntryId(Long doctorCatalogEntryId) {
        this.doctorCatalogEntryId = doctorCatalogEntryId;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
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

    public String getQualification() {
        return qualification;
    }

    public void setQualification(String qualification) {
        this.qualification = qualification;
    }

    public Integer getExperienceYears() {
        return experienceYears;
    }

    public void setExperienceYears(Integer experienceYears) {
        this.experienceYears = experienceYears;
    }

    public BigDecimal getConsultationFee() {
        return consultationFee;
    }

    public void setConsultationFee(BigDecimal consultationFee) {
        this.consultationFee = consultationFee;
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

    public Integer getPosition() {
        return position;
    }

    public void setPosition(Integer position) {
        this.position = position;
    }

    public Integer getPredictedWaitMinutes() {
        return predictedWaitMinutes;
    }

    public void setPredictedWaitMinutes(Integer predictedWaitMinutes) {
        this.predictedWaitMinutes = predictedWaitMinutes;
    }

    public String getMatchReason() {
        return matchReason;
    }

    public void setMatchReason(String matchReason) {
        this.matchReason = matchReason;
    }
}
