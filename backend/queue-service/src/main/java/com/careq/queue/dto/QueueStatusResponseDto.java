package com.careq.queue.dto;

/**
 * Response for GET /api/queue/my-status.
 * active=false means the patient is not currently in any queue.
 */
public class QueueStatusResponseDto {

    private boolean active;
    private QueueEntryResponseDto entry;

    public QueueStatusResponseDto() {
    }

    public QueueStatusResponseDto(boolean active, QueueEntryResponseDto entry) {
        this.active = active;
        this.entry = entry;
    }

    public boolean isActive() {
        return active;
    }

    public void setActive(boolean active) {
        this.active = active;
    }

    public QueueEntryResponseDto getEntry() {
        return entry;
    }

    public void setEntry(QueueEntryResponseDto entry) {
        this.entry = entry;
    }
}
