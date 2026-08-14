package com.careq.notification.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;

import java.time.LocalDateTime;

/**
 * Day 16 — per-user delivery preferences. One row per user (upserted by
 * {@code PUT /api/notifications/preferences}); a missing row means "all
 * defaults on" — {@link #webPushEnabled} defaults to true, so an existing
 * patient who never opened settings still receives pushes once they enable
 * the browser permission.
 *
 * <p>The preference is the user's opt-out switch; the browser permission is
 * the separate, OS-level opt-in. Both must be true for delivery.
 */
@Entity
@Table(name = "notification_preferences")
public class NotificationPreference {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "recipient_user_id", length = 36, nullable = false, unique = true)
    private String recipientUserId;

    @Column(name = "web_push_enabled", nullable = false)
    private boolean webPushEnabled = true;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    public NotificationPreference() {
    }

    public NotificationPreference(String recipientUserId, boolean webPushEnabled) {
        this.recipientUserId = recipientUserId;
        this.webPushEnabled = webPushEnabled;
        this.createdAt = LocalDateTime.now();
        this.updatedAt = LocalDateTime.now();
    }

    @PrePersist
    protected void onCreate() {
        if (createdAt == null) {
            createdAt = LocalDateTime.now();
        }
        if (updatedAt == null) {
            updatedAt = LocalDateTime.now();
        }
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }

    public Long getId() {
        return id;
    }

    public String getRecipientUserId() {
        return recipientUserId;
    }

    public void setRecipientUserId(String recipientUserId) {
        this.recipientUserId = recipientUserId;
    }

    public boolean isWebPushEnabled() {
        return webPushEnabled;
    }

    public void setWebPushEnabled(boolean webPushEnabled) {
        this.webPushEnabled = webPushEnabled;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }
}
