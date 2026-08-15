package com.careq.queue.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;

import java.time.LocalDateTime;

/**
 * A patient's position in a doctor's queue.
 *
 * Design notes :
 * - The doctor's consultation data (avgConsultationTimeMinutes, isAvailable)
 *   is NOT stored here — it is fetched live from doctor-service via the
 *   DoctorServiceClient Feign client. This keeps a single source of truth.
 * - Queue position and predicted wait time are DERIVED values, recalculated
 *   on every read from the live queue. They are never persisted.
 * - Effective triage = doctorOverrideTriage (if set) else aiSuggestedTriage.
 *   The doctor's override is always final.
 */
@Entity
@Table(name = "queue_entries")
public class QueueEntry {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** Patient user id (UUID), propagated via X-User-Id header. Plain reference — no FK across microservices. */
    @Column(name = "patient_id", length = 36, nullable = false)
    private String patientId;

    /**
     * Patient display name captured at join time . Nullable so rows
     * created before this column existed still load — the UI falls back to a
     * short ID when null.
     */
    @Column(name = "patient_name", length = 255)
    private String patientName;

    /** Doctor catalog entry id (doctor-service's DoctorCatalogEntry.id). Plain reference — no FK. */
    @Column(name = "doctor_catalog_entry_id", nullable = false)
    private Long doctorCatalogEntryId;

    @Column(name = "symptom_text", length = 2000, nullable = false)
    private String symptomText;

    @Enumerated(EnumType.STRING)
    @Column(name = "ai_suggested_triage", length = 20, nullable = false)
    private TriageLevel aiSuggestedTriage;

    @Enumerated(EnumType.STRING)
    @Column(name = "doctor_override_triage", length = 20)
    private TriageLevel doctorOverrideTriage;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", length = 20, nullable = false)
    private QueueStatus status = QueueStatus.WAITING;

    @Column(name = "joined_at", nullable = false, updatable = false)
    private LocalDateTime joinedAt;

    @Column(name = "called_at")
    private LocalDateTime calledAt;

    @Column(name = "completed_at")
    private LocalDateTime completedAt;

    public QueueEntry() {
    }

    @PrePersist
    protected void onCreate() {
        if (joinedAt == null) {
            joinedAt = LocalDateTime.now();
        }
        if (status == null) {
            status = QueueStatus.WAITING;
        }
    }

    /**
     * Effective triage used for queue ordering:
     * the doctor's override is final when present, otherwise the AI suggestion.
     */
    public TriageLevel effectiveTriage() {
        return doctorOverrideTriage != null ? doctorOverrideTriage : aiSuggestedTriage;
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

    public QueueStatus getStatus() {
        return status;
    }

    public void setStatus(QueueStatus status) {
        this.status = status;
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
