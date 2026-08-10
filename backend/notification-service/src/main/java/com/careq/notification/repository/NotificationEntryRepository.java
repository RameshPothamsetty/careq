package com.careq.notification.repository;

import com.careq.notification.entity.NotificationEntry;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface NotificationEntryRepository extends JpaRepository<NotificationEntry, Long> {

    /** The caller's own notifications, newest first. */
    Page<NotificationEntry> findByRecipientUserIdOrderByCreatedAtDesc(String recipientUserId, Pageable pageable);

    /** Unread count for the badge — cheap aggregate on the recipient index. */
    long countByRecipientUserIdAndReadFalse(String recipientUserId);
}
