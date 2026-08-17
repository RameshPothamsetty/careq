package com.careq.doctor.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.math.BigDecimal;

/**
 * One doctor from the ranked relevance search (RAG-style retrieval over the
 * catalog). Carries the same fields as {@link DoctorCatalogResponseDto} plus a
 * {@code relevanceScore} (0-100) explaining how well the entry matched the
 * query — the UI can sort by it and the chat assistant can cite it.
 */
@Schema(description = "A doctor catalog entry with a relevance score for a free-text search.")
public class DoctorSearchResultDto {

    @Schema(description = "Catalog entry ID", example = "1")
    private Long id;

    @Schema(description = "Doctor's display name", example = "Dr. Arjun Sharma")
    private String name;

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

    @Schema(description = "Relevance score 0-100 — higher means a better match to the query", example = "92")
    private int relevanceScore;

    public DoctorSearchResultDto() {
    }

    public DoctorSearchResultDto(DoctorCatalogResponseDto dto, int relevanceScore) {
        this.id = dto.getId();
        this.name = dto.getName();
        this.departmentName = dto.getDepartmentName();
        this.specialization = dto.getSpecialization();
        this.qualification = dto.getQualification();
        this.experienceYears = dto.getExperienceYears();
        this.consultationFee = dto.getConsultationFee();
        this.avgConsultationTimeMinutes = dto.getAvgConsultationTimeMinutes();
        this.isAvailable = dto.getIsAvailable();
        this.relevanceScore = relevanceScore;
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

    public int getRelevanceScore() {
        return relevanceScore;
    }

    public void setRelevanceScore(int relevanceScore) {
        this.relevanceScore = relevanceScore;
    }
}
