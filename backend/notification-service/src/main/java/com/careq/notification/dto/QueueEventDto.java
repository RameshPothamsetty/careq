package com.careq.notification.dto;

import com.fasterxml.jackson.annotation.JsonFormat;

import java.time.LocalDateTime;

/**
 * the JSON payload consumed from the {@code careq.events} topic
 * exchange. Field names mirror queue-service's {@code QueueEventDto} exactly
 * (this codebase has no shared module — the JSON contract is the field names).
 *
 * <p>Plain class with getters/setters (not a record) for maximum Jackson
 * compatibility with the {@code Jackson2JsonMessageConverter} — matches the
 * wider codebase convention.
 */
public class QueueEventDto {

    private String eventType;
    private String recipientUserId;
    private Long queueEntryId;
    private String patientName;
    private String doctorName;
    private String departmentName;
    private Integer position;
    private String triageLevel;
    @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss")
    private LocalDateTime timestamp;

    public QueueEventDto() {
    }

    public QueueEventDto(String eventType, String recipientUserId, Long queueEntryId,
                         String patientName, String doctorName, String departmentName,
                         Integer position, String triageLevel, LocalDateTime timestamp) {
        this.eventType = eventType;
        this.recipientUserId = recipientUserId;
        this.queueEntryId = queueEntryId;
        this.patientName = patientName;
        this.doctorName = doctorName;
        this.departmentName = departmentName;
        this.position = position;
        this.triageLevel = triageLevel;
        this.timestamp = timestamp;
    }

    public String getEventType() { return eventType; }
    public void setEventType(String eventType) { this.eventType = eventType; }

    public String getRecipientUserId() { return recipientUserId; }
    public void setRecipientUserId(String recipientUserId) { this.recipientUserId = recipientUserId; }

    public Long getQueueEntryId() { return queueEntryId; }
    public void setQueueEntryId(Long queueEntryId) { this.queueEntryId = queueEntryId; }

    public String getPatientName() { return patientName; }
    public void setPatientName(String patientName) { this.patientName = patientName; }

    public String getDoctorName() { return doctorName; }
    public void setDoctorName(String doctorName) { this.doctorName = doctorName; }

    public String getDepartmentName() { return departmentName; }
    public void setDepartmentName(String departmentName) { this.departmentName = departmentName; }

    public Integer getPosition() { return position; }
    public void setPosition(Integer position) { this.position = position; }

    public String getTriageLevel() { return triageLevel; }
    public void setTriageLevel(String triageLevel) { this.triageLevel = triageLevel; }

    public LocalDateTime getTimestamp() { return timestamp; }
    public void setTimestamp(LocalDateTime timestamp) { this.timestamp = timestamp; }
}