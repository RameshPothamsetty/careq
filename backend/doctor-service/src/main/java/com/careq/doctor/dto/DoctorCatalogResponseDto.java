package com.careq.doctor.dto;

import com.careq.doctor.entity.DoctorCatalogEntry;
import io.swagger.v3.oas.annotations.media.Schema;

import java.math.BigDecimal;
import java.io.Serializable;

@Schema(description = "A doctor catalog entry with its department name and live availability.")
public class DoctorCatalogResponseDto implements Serializable {

    private static final long serialVersionUID = 1L;

    @Schema(description = "Catalog entry ID", example = "1")
    private Long id;

    @Schema(description = "Doctor's display name", example = "Dr. Arjun Sharma")
    private String name;

    @Schema(description = "User UUID the entry belongs to", example = "550e8400-e29b-41d4-a716-446655440001")
    private String userId;

    @Schema(description = "Department ID", example = "1")
    private Long departmentId;

    @Schema(description = "Department display name", example = "Cardiology")
    private String departmentName;

    @Schema(description = "Medical specialization", example = "Interventional Cardiology")
    private String specialization;

    @Schema(description = "Qualifications", example = "MD, DM Cardiology")
    private String qualification;

    @Schema(description = "Years of experience", example = "12")
    private Integer experienceYears;

    @Schema(description = "Consultation fee", example = "500.00")
    private BigDecimal consultationFee;

    @Schema(description = "Average consultation time in minutes (drives wait prediction)", example = "15")
    private Integer avgConsultationTimeMinutes;

    @Schema(description = "Whether the doctor is currently accepting queue joins", example = "true")
    private Boolean isAvailable;

    public DoctorCatalogResponseDto() {
    }

    public static DoctorCatalogResponseDto fromEntity(DoctorCatalogEntry entry, String departmentName) {
        DoctorCatalogResponseDto dto = new DoctorCatalogResponseDto();
        dto.setId(entry.getId());
        dto.setName(entry.getName());
        dto.setUserId(entry.getUserId());
        dto.setDepartmentId(entry.getDepartmentId());
        dto.setDepartmentName(departmentName);
        dto.setSpecialization(entry.getSpecialization());
        dto.setQualification(entry.getQualification());
        dto.setExperienceYears(entry.getExperienceYears());
        dto.setConsultationFee(entry.getConsultationFee());
        dto.setAvgConsultationTimeMinutes(entry.getAvgConsultationTimeMinutes());
        dto.setIsAvailable(entry.getIsAvailable());
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
}
