package com.careq.notification.dto;

import com.careq.notification.entity.NotificationEntry;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDateTime;

@Schema(description = "A persisted in-app notification.")
public class NotificationResponseDto {

    @Schema(description = "Notification ID", example = "1")
    private Long id;

    @Schema(description = "Event type: queue.joined | queue.triaged | queue.called | queue.completed",
            example = "queue.called")
    private String type;

    @Schema(description = "Human-readable message", example = "Dr. Arjun Sharma has called you — please head to the consultation room.")
    private String message;

    @Schema(description = "Whether the recipient has read it", example = "false")
    private boolean read;

    @Schema(description = "When the event happened", example = "2026-08-10T09:00:00")
    private LocalDateTime createdAt;

    public NotificationResponseDto() {
    }

    public static NotificationResponseDto fromEntity(NotificationEntry entry) {
        NotificationResponseDto dto = new NotificationResponseDto();
        dto.setId(entry.getId());
        dto.setType(entry.getType());
        dto.setMessage(entry.getMessage());
        dto.setRead(entry.isRead());
        dto.setCreatedAt(entry.getCreatedAt());
        return dto;
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }

    public boolean isRead() {
        return read;
    }

    public void setRead(boolean read) {
        this.read = read;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }
}
