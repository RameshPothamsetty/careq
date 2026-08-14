package com.careq.notification.service;

import com.careq.notification.dto.NotificationPreferenceResponseDto;
import com.careq.notification.dto.PushSubscriptionRequestDto;
import com.careq.notification.entity.NotificationPreference;
import com.careq.notification.entity.PushSubscription;
import com.careq.notification.repository.NotificationPreferenceRepository;
import com.careq.notification.repository.PushSubscriptionRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Day 16 — per-user delivery preferences + Web Push subscription registry.
 *
 * <p><b>Preference semantics:</b> a missing preference row means "all defaults
 * on" ({@code webPushEnabled = true}) — an existing patient who never opened
 * settings still receives pushes once they grant the browser permission. The
 * preference row only exists once the user has actively changed it.
 *
 * <p><b>Subscription semantics:</b> subscriptions are upserted by endpoint.
 * One browser = one subscription; if a different user re-registers the same
 * endpoint (session switch on a shared device), the row is re-assigned to the
 * caller so delivery never goes to the wrong person.
 */
@Service
public class PushPreferenceService {

    private final NotificationPreferenceRepository preferenceRepository;
    private final PushSubscriptionRepository subscriptionRepository;

    public PushPreferenceService(NotificationPreferenceRepository preferenceRepository,
                                 PushSubscriptionRepository subscriptionRepository) {
        this.preferenceRepository = preferenceRepository;
        this.subscriptionRepository = subscriptionRepository;
    }

    @Transactional(readOnly = true)
    public NotificationPreferenceResponseDto getPreferences(String userId) {
        return NotificationPreferenceResponseDto.fromEntity(
                preferenceRepository.findByRecipientUserId(userId).orElse(null));
    }

    @Transactional
    public NotificationPreferenceResponseDto updatePreferences(String userId, boolean webPushEnabled) {
        NotificationPreference preference = preferenceRepository.findByRecipientUserId(userId)
                .orElseGet(() -> new NotificationPreference(userId, webPushEnabled));
        preference.setWebPushEnabled(webPushEnabled);
        return NotificationPreferenceResponseDto.fromEntity(preferenceRepository.save(preference));
    }

    @Transactional
    public void registerSubscription(String userId, PushSubscriptionRequestDto request) {
        String endpoint = request.getEndpoint().trim();
        String p256dh = request.getKeys().getP256dh().trim();
        String auth = request.getKeys().getAuth().trim();

        // Upsert by endpoint: a re-subscribe (or a session switch on the same
        // browser) must replace the stale row, not duplicate it.
        subscriptionRepository.findByEndpoint(endpoint).ifPresentOrElse(
                existing -> {
                    existing.setRecipientUserId(userId);
                    existing.setP256dh(p256dh);
                    existing.setAuth(auth);
                    subscriptionRepository.save(existing);
                },
                () -> subscriptionRepository.save(
                        new PushSubscription(userId, endpoint, p256dh, auth)));
    }

    @Transactional
    public void unregisterSubscription(String userId, String endpoint) {
        if (endpoint == null || endpoint.isBlank()) {
            return;
        }
        subscriptionRepository.deleteByEndpointAndRecipientUserId(endpoint.trim(), userId);
    }
}
