package com.careq.notification.dto;

import jakarta.validation.constraints.NotNull;

/**
 * update payload for {@code PUT /api/notifications/preferences}.
 * The flag is required (a missing value is a client bug, not a no-op).
 */
public class UpdateNotificationPreferenceRequestDto {

    @NotNull(message = "webPushEnabled is required")
    private Boolean webPushEnabled;

    public UpdateNotificationPreferenceRequestDto() {
    }

    public UpdateNotificationPreferenceRequestDto(Boolean webPushEnabled) {
        this.webPushEnabled = webPushEnabled;
    }

    public Boolean getWebPushEnabled() {
        return webPushEnabled;
    }

    public void setWebPushEnabled(Boolean webPushEnabled) {
        this.webPushEnabled = webPushEnabled;
    }
}
