package com.careq.queue.dto;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * Response for GET /api/queue/my-status (fully documented).
 * active=false means the patient is not currently in any queue.
 */
@Schema(description = "The calling patient's current queue status.")
public class QueueStatusResponseDto {

    @Schema(description = "Whether the patient currently has an active queue entry", example = "true")
    private boolean active;

    @Schema(description = "Active entry with fresh position/wait, or null when inactive")
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
