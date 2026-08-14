package com.careq.notification.repository;

import com.careq.notification.entity.PushSubscription;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

/**
 * Day 16 — Web Push subscriptions, keyed by endpoint (unique per browser/device).
 */
public interface PushSubscriptionRepository extends JpaRepository<PushSubscription, Long> {

    List<PushSubscription> findByRecipientUserId(String recipientUserId);

    Optional<PushSubscription> findByEndpointAndRecipientUserId(String endpoint, String recipientUserId);

    Optional<PushSubscription> findByEndpoint(String endpoint);

    void deleteByEndpointAndRecipientUserId(String endpoint, String recipientUserId);

    void deleteByEndpoint(String endpoint);
}
