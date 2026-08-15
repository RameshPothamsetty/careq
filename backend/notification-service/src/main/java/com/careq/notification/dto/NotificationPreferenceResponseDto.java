package com.careq.notification.dto;

import com.careq.notification.entity.NotificationPreference;

/**
 * the caller's delivery preferences.
 * {@code webPushEnabled} is the user's opt-out switch (defaults to ON when no
 * preference row exists yet); the OS-level browser permission is tracked by
 * the browser itself, so a subscription row only exists once the user granted
 * it. Both must be true for actual delivery.
 */
public class NotificationPreferenceResponseDto {

    private boolean webPushEnabled;

    public NotificationPreferenceResponseDto() {
    }

    public NotificationPreferenceResponseDto(boolean webPushEnabled) {
        this.webPushEnabled = webPushEnabled;
    }

    public static NotificationPreferenceResponseDto fromEntity(NotificationPreference preference) {
        return new NotificationPreferenceResponseDto(
                preference == null || preference.isWebPushEnabled());
    }

    public boolean isWebPushEnabled() {
        return webPushEnabled;
    }

    public void setWebPushEnabled(boolean webPushEnabled) {
        this.webPushEnabled = webPushEnabled;
    }
}
