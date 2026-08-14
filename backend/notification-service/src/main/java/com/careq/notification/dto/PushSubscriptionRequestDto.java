package com.careq.notification.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.validation.constraints.NotBlank;

/**
 * Day 16 — the browser's {@code PushSubscriptionJSON} as sent by
 * {@code POST /api/notifications/push/subscriptions}. Field names mirror the
 * Web Push spec's subscription JSON exactly:
 *
 * <pre>
 * {
 *   "endpoint": "https://fcm.googleapis.com/fcm/send/...",
 *   "keys": { "p256dh": "base64url...", "auth": "base64url..." }
 * }
 * </pre>
 *
 * The {@code expirationTime} field from the browser is ignored (nullable and
 * irrelevant for our always-deliver semantics).
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class PushSubscriptionRequestDto {

    @NotBlank(message = "endpoint is required")
    private String endpoint;

    private Keys keys;

    public PushSubscriptionRequestDto() {
    }

    public PushSubscriptionRequestDto(String endpoint, Keys keys) {
        this.endpoint = endpoint;
        this.keys = keys;
    }

    public String getEndpoint() {
        return endpoint;
    }

    public void setEndpoint(String endpoint) {
        this.endpoint = endpoint;
    }

    public Keys getKeys() {
        return keys;
    }

    public void setKeys(Keys keys) {
        this.keys = keys;
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Keys {

        @NotBlank(message = "keys.p256dh is required")
        private String p256dh;

        @NotBlank(message = "keys.auth is required")
        private String auth;

        public Keys() {
        }

        public Keys(String p256dh, String auth) {
            this.p256dh = p256dh;
            this.auth = auth;
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
    }
}
