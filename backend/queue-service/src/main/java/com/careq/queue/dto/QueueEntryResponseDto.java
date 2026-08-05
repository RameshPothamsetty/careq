package com.careq.queue.dto;

import com.careq.queue.entity.QueueEntry;
import com.careq.queue.entity.QueueStatus;
import com.careq.queue.entity.TriageLevel;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDateTime;

/**
 * Queue entry view model (Day 9: fully documented). Position and
 * predictedWaitMinutes are derived live on every read — never persisted.
 */
@Schema(description = "A queue entry with derived position and predicted wait.")
public class QueueEntryResponseDto {

    @Schema(description = "Queue entry ID", example = "1")
    private Long id;

    @Schema(description = "Patient's user UUID", example = "550e8400-e29b-41d4-a716-446655440010")
    private String patientId;

    @Schema(description = "Patient's display name", example = "John Patient")
    private String patientName;

    @Schema(description = "Doctor catalog entry ID", example = "1")
    private Long doctorCatalogEntryId;

    @Schema(description = "Doctor's display name", example = "Dr. Arjun Sharma")
    private String doctorName;

    @Schema(description = "Department display name", example = "Cardiology")
    private String departmentName;

    @Schema(description = "Doctor's specialization", example = "Interventional Cardiology")
    private String specialization;

    @Schema(description = "Patient's reported symptoms", example = "Severe chest pain radiating to my left arm")
    private String symptomText;

    @Schema(description = "AI-suggested triage (from Groq LLM)", example = "EMERGENCY", allowableValues = {"EMERGENCY", "HIGH", "NORMAL", "FOLLOW_UP"})
    private TriageLevel aiSuggestedTriage;

    @Schema(description = "Doctor's final override, when set (null = AI suggestion stands)", example = "null", nullable = true, allowableValues = {"EMERGENCY", "HIGH", "NORMAL", "FOLLOW_UP"})
    private TriageLevel doctorOverrideTriage;

    @Schema(description = "Effective triage driving queue order (override if present, else AI value)", example = "EMERGENCY", allowableValues = {"EMERGENCY", "HIGH", "NORMAL", "FOLLOW_UP"})
    private TriageLevel effectiveTriage;

    @Schema(description = "Queue state", example = "WAITING", allowableValues = {"WAITING", "IN_PROGRESS", "COMPLETED", "CANCELLED"})
    private QueueStatus status;

    @Schema(description = "Position in the effective queue order (1 = next to be seen); null once completed/cancelled", example = "1")
    private Integer position;

    @Schema(description = "Predicted wait in minutes: (patients ahead) x avgConsultationTimeMinutes; null once completed/cancelled", example = "30")
    private Integer predictedWaitMinutes;

    @Schema(description = "When the patient joined", example = "2026-07-31T09:00:00")
    private LocalDateTime joinedAt;

    @Schema(description = "When the patient was called (IN_PROGRESS)", example = "null", nullable = true)
    private LocalDateTime calledAt;

    @Schema(description = "When the consultation completed", example = "null", nullable = true)
    private LocalDateTime completedAt;

    public QueueEntryResponseDto() {
    }

    public static QueueEntryResponseDto fromEntity(QueueEntry entry) {
        QueueEntryResponseDto dto = new QueueEntryResponseDto();
        dto.setId(entry.getId());
        dto.setPatientId(entry.getPatientId());
        dto.setPatientName(entry.getPatientName());
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

    public String getPatientName() {
        return patientName;
    }

    public void setPatientName(String patientName) {
        this.patientName = patientName;
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
