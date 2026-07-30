package com.careq.doctor.dto;

import jakarta.validation.constraints.*;
import java.math.BigDecimal;

public class DoctorCatalogRequestDto {

    @NotBlank(message = "User ID is required")
    private String userId;

    @NotNull(message = "Department ID is required")
    private Long departmentId;

    @NotBlank(message = "Specialization is required")
    @Size(max = 255, message = "Specialization must not exceed 255 characters")
    private String specialization;

    @NotBlank(message = "Qualification is required")
    @Size(max = 500, message = "Qualification must not exceed 500 characters")
    private String qualification;

    @NotNull(message = "Experience years is required")
    @Min(value = 0, message = "Experience years must be at least 0")
    private Integer experienceYears;

    @NotNull(message = "Consultation fee is required")
    @DecimalMin(value = "0.0", message = "Consultation fee must be 0 or greater")
    private BigDecimal consultationFee;

    @NotNull(message = "Average consultation time is required")
    @Min(value = 1, message = "Average consultation time must be at least 1 minute")
    private Integer avgConsultationTimeMinutes;

    public DoctorCatalogRequestDto() {
    }

    public String getUserId() {
        return userId;
    }

    public void setUserId(String userId) {
        this.userId = userId;
    }

    public Long getDepartmentId() {
        return departmentId;
    }

    public void setDepartmentId(Long departmentId) {
        this.departmentId = departmentId;
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
}
