package com.careq.notification.service;

import com.careq.notification.dto.NotificationListResponse;
import com.careq.notification.dto.NotificationResponseDto;

public interface NotificationService {

    /**
     * The caller's own notifications, newest first, paginated. The response
     * carries a total unread count for the badge (independent of the page).
     */
    NotificationListResponse getMyNotifications(String recipientUserId, int page, int size);

    /**
     * Marks a notification as read. Ownership-checked: the recipient marks
     * their own; admins may mark any; anyone else is forbidden.
     */
    NotificationResponseDto markRead(Long id, String requesterUserId, String requesterRole);
}
