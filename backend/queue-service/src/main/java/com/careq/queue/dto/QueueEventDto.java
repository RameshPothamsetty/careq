package com.careq.queue.dto;

import com.fasterxml.jackson.annotation.JsonFormat;

import java.time.LocalDateTime;

/**
 * the JSON payload published by queue-service onto the
 * {@code careq.events} topic exchange after each queue state transition.
 *
 * <p>Deliberately small and self-contained: the recipient, the transition type
 * and just enough context (doctor name, position, triage) for the consumer
 * (notification-service) to compose a human-readable notification. The same
 * JSON shape is mirrored in notification-service's dto package — there is no
 * shared module in this codebase, so the contract is the field names.
 *
 * <p>Plain class with getters/setters (not a record) for maximum Jackson
 * compatibility with the {@code Jackson2JsonMessageConverter} used by the
 * RabbitTemplate — matches the wider codebase convention.
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
        this.timestamp = timestamp != null ? timestamp : LocalDateTime.now();
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