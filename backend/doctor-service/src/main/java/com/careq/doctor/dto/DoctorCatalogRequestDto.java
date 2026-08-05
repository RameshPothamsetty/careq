package com.careq.doctor.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.*;

import java.math.BigDecimal;

@Schema(description = "Doctor catalog create/update payload.")
public class DoctorCatalogRequestDto {

    @Schema(description = "Doctor's display name", example = "Dr. Arjun Sharma", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotBlank(message = "Name is required")
    @Size(max = 255, message = "Name must not exceed 255 characters")
    private String name;

    @Schema(description = "User UUID this catalog entry belongs to (must be unique)", example = "550e8400-e29b-41d4-a716-446655440001", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotBlank(message = "User ID is required")
    private String userId;

    @Schema(description = "Department ID this doctor belongs to", example = "1", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotNull(message = "Department ID is required")
    private Long departmentId;

    @Schema(description = "Medical specialization", example = "Interventional Cardiology", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotBlank(message = "Specialization is required")
    @Size(max = 255, message = "Specialization must not exceed 255 characters")
    private String specialization;

    @Schema(description = "Qualifications", example = "MD, DM Cardiology", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotBlank(message = "Qualification is required")
    @Size(max = 500, message = "Qualification must not exceed 500 characters")
    private String qualification;

    @Schema(description = "Years of experience", example = "12", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotNull(message = "Experience years is required")
    @Min(value = 0, message = "Experience years must be at least 0")
    private Integer experienceYears;

    @Schema(description = "Consultation fee", example = "500.00", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotNull(message = "Consultation fee is required")
    @DecimalMin(value = "0.0", message = "Consultation fee must be 0 or greater")
    private BigDecimal consultationFee;

    @Schema(description = "Average consultation time in minutes (drives wait prediction)", example = "15", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotNull(message = "Average consultation time is required")
    @Min(value = 1, message = "Average consultation time must be at least 1 minute")
    private Integer avgConsultationTimeMinutes;

    public DoctorCatalogRequestDto() {
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
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
