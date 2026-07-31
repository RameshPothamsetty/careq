package com.careq.queue.dto;

import com.careq.queue.entity.QueueEntry;
import com.careq.queue.entity.QueueStatus;
import com.careq.queue.entity.TriageLevel;

import java.time.LocalDateTime;

/**
 * Queue entry view model. Position and predictedWaitMinutes are derived
 * live on every read — never persisted.
 */
public class QueueEntryResponseDto {

    private Long id;
    private String patientId;
    private Long doctorCatalogEntryId;
    private String doctorName;
    private String departmentName;
    private String specialization;
    private String symptomText;
    private TriageLevel aiSuggestedTriage;
    private TriageLevel doctorOverrideTriage;
    private TriageLevel effectiveTriage;
    private QueueStatus status;
    private Integer position;
    private Integer predictedWaitMinutes;
    private LocalDateTime joinedAt;
    private LocalDateTime calledAt;
    private LocalDateTime completedAt;

    public QueueEntryResponseDto() {
    }

    public static QueueEntryResponseDto fromEntity(QueueEntry entry) {
        QueueEntryResponseDto dto = new QueueEntryResponseDto();
        dto.setId(entry.getId());
        dto.setPatientId(entry.getPatientId());
        dto.setDoctorCatalogEntryId(entry.getDoctorCatalogEntryId());
        dto.setSymptomText(entry.getSymptomText());
        dto.setAiSuggestedTriage(entry.getAiSuggestedTriage());
        dto.setDoctorOverrideTriage(entry.getDoctorOverrideTriage());
        dto.setEffectiveTriage(entry.effectiveTriage());
        dto.setStatus(entry.getStatus());
        dto.setJoinedAt(entry.getJoinedAt());
        dto.setCalledAt(entry.getCalledAt());
        dto.setCompletedAt(entry.getCompletedAt());
        return dto;
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getPatientId() {
        return patientId;
    }

    public void setPatientId(String patientId) {
        this.patientId = patientId;
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

    public String getSymptomText() {
        return symptomText;
    }

    public void setSymptomText(String symptomText) {
        this.symptomText = symptomText;
    }

    public TriageLevel getAiSuggestedTriage() {
        return aiSuggestedTriage;
    }

    public void setAiSuggestedTriage(TriageLevel aiSuggestedTriage) {
        this.aiSuggestedTriage = aiSuggestedTriage;
    }

    public TriageLevel getDoctorOverrideTriage() {
        return doctorOverrideTriage;
    }

    public void setDoctorOverrideTriage(TriageLevel doctorOverrideTriage) {
        this.doctorOverrideTriage = doctorOverrideTriage;
    }

    public TriageLevel getEffectiveTriage() {
        return effectiveTriage;
    }

    public void setEffectiveTriage(TriageLevel effectiveTriage) {
        this.effectiveTriage = effectiveTriage;
    }

    public QueueStatus getStatus() {
        return status;
    }

    public void setStatus(QueueStatus status) {
        this.status = status;
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

    public LocalDateTime getJoinedAt() {
        return joinedAt;
    }

    public void setJoinedAt(LocalDateTime joinedAt) {
        this.joinedAt = joinedAt;
    }

    public LocalDateTime getCalledAt() {
        return calledAt;
    }

    public void setCalledAt(LocalDateTime calledAt) {
        this.calledAt = calledAt;
    }

    public LocalDateTime getCompletedAt() {
        return completedAt;
    }

    public void setCompletedAt(LocalDateTime completedAt) {
        this.completedAt = completedAt;
    }
}
