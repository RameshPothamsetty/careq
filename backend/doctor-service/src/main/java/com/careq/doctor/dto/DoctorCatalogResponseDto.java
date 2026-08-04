package com.careq.doctor.dto;

import com.careq.doctor.entity.DoctorCatalogEntry;
import java.math.BigDecimal;

public class DoctorCatalogResponseDto {

    private Long id;
    private String name;
    private String userId;
    private Long departmentId;
    private String departmentName;
    private String specialization;
    private String qualification;
    private Integer experienceYears;
    private BigDecimal consultationFee;
    private Integer avgConsultationTimeMinutes;
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
