package com.careq.notification.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;

import java.time.LocalDateTime;

/**
 * a browser Web Push subscription (one row per device/browser).
 * Registered by the SPA via {@code POST /api/notifications/push/subscriptions}
 * after the user grants the Notification permission; the delivery engine reads
 * these rows to send the encrypted push payload when a queue event arrives.
 *
 * <p>{@code recipientUserId} is a plain reference to auth-service's users.id
 * (microservice boundary, no FK) — the same pattern as {@link NotificationEntry}.
 * The endpoint is the push service URL (FCM/APNs/Mozilla) and the keys are the
 * base64url p256dh/auth values from the browser's {@code PushSubscription}.
 */
@Entity
@Table(name = "push_subscriptions", indexes = {
        @Index(name = "idx_push_recipient", columnList = "recipient_user_id"),
        @Index(name = "idx_push_endpoint", columnList = "endpoint", unique = true)
})
public class PushSubscription {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "recipient_user_id", length = 36, nullable = false)
    private String recipientUserId;

    /** Push service endpoint (https://fcm.googleapis.com/... or similar). */
    @Column(name = "endpoint", length = 1000, nullable = false, unique = true)
    private String endpoint;

    /** base64url client public key (p256dh) from the browser subscription. */
    @Column(name = "p256dh", length = 255, nullable = false)
    private String p256dh;

    /** base64url shared auth secret from the browser subscription. */
    @Column(name = "auth", length = 255, nullable = false)
    private String auth;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    public PushSubscription() {
    }

    public PushSubscription(String recipientUserId, String endpoint, String p256dh, String auth) {
        this.recipientUserId = recipientUserId;
        this.endpoint = endpoint;
        this.p256dh = p256dh;
        this.auth = auth;
        this.createdAt = LocalDateTime.now();
    }

    @PrePersist
    protected void onCreate() {
        if (createdAt == null) {
            createdAt = LocalDateTime.now();
        }
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

    public String getEndpoint() {
        return endpoint;
    }

    public void setEndpoint(String endpoint) {
        this.endpoint = endpoint;
    }

    public String getP256dh() {
        return p256dh;
    }

    public void setP256dh(String p256dh) {
        this.p256dh = p256dh;
    }

    public String getAuth() {
        return auth;
    }

    public void setAuth(String auth) {
        this.auth = auth;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }
}
