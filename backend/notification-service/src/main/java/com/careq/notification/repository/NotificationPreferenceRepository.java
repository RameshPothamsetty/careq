package com.careq.notification.repository;

import com.careq.notification.entity.NotificationPreference;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

/**
 * Day 16 — per-user notification preferences (upsert by recipient_user_id).
 */
public interface NotificationPreferenceRepository extends JpaRepository<NotificationPreference, Long> {

    Optional<NotificationPreference> findByRecipientUserId(String recipientUserId);
}
