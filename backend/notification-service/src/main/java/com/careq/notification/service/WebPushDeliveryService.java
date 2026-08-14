package com.careq.notification.service;

import com.careq.notification.entity.NotificationPreference;
import com.careq.notification.entity.PushSubscription;
import com.careq.notification.repository.NotificationPreferenceRepository;
import com.careq.notification.repository.PushSubscriptionRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import nl.martijndwars.webpush.Notification;
import nl.martijndwars.webpush.PushService;
import nl.martijndwars.webpush.Urgency;
import org.apache.http.HttpResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

import java.security.Security;
import java.util.List;
import java.util.Map;

/**
 * Day 16 — Web Push (VAPID) delivery for queue events.
 *
 * <p><b>Fail-open by design (the codebase's house rule):</b> push delivery is
 * strictly best-effort and fully detached from the in-app flow. The RabbitMQ
 * consumer persists the in-app notification FIRST and then fires this class's
 * {@link #deliverAsync} on a background thread — every failure is logged and
 * swallowed, never propagated. A patient being called is NEVER blocked because
 * a push service was slow or the DB hiccupped.
 *
 * <p><b>Configuration:</b> both VAPID keys must be present
 * ({@code VAPID_PUBLIC_KEY} / {@code VAPID_PRIVATE_KEY}, base64url strings from
 * {@code npx web-push generate-vapid-keys}); otherwise delivery is disabled at
 * construction time with a logged warning and in-app notifications keep
 * working unchanged — the same pattern as the empty {@code GROQ_API_KEY}.
 *
 * <p><b>Delivery rules:</b> the user's opt-out preference is honored; dead
 * subscriptions (push service returns 404/410 — browser unsubscribed or the
 * endpoint expired) are deleted so we never pay for them again; anything else
 * is logged and retried on the next event.
 */
@Component
public class WebPushDeliveryService {

    private static final Logger log = LoggerFactory.getLogger(WebPushDeliveryService.class);

    static {
        // web-push 5.1.1 requests the "BC" provider for ECDH key generation.
        Security.addProvider(new org.bouncycastle.jce.provider.BouncyCastleProvider());
    }

    private final PushSubscriptionRepository subscriptionRepository;
    private final NotificationPreferenceRepository preferenceRepository;
    private final ObjectMapper objectMapper;

    /** Null when VAPID keys are not configured → delivery silently disabled. */
    private final PushService pushService;

    @Autowired
    public WebPushDeliveryService(PushSubscriptionRepository subscriptionRepository,
                                  NotificationPreferenceRepository preferenceRepository,
                                  ObjectMapper objectMapper,
                                  @Value("${push.vapid.public-key:}") String publicKey,
                                  @Value("${push.vapid.private-key:}") String privateKey,
                                  @Value("${push.vapid.subject:mailto:careq@example.com}") String subject) {
        this(subscriptionRepository, preferenceRepository, objectMapper,
                buildPushService(publicKey, privateKey, subject));
    }

    /** Package-private seam for tests: inject a PushService directly (real or mock). */
    WebPushDeliveryService(PushSubscriptionRepository subscriptionRepository,
                           NotificationPreferenceRepository preferenceRepository,
                           ObjectMapper objectMapper,
                           PushService pushService) {
        this.subscriptionRepository = subscriptionRepository;
        this.preferenceRepository = preferenceRepository;
        this.objectMapper = objectMapper;
        this.pushService = pushService;
    }

    /**
     * Fire-and-forget push delivery. Runs on the {@code webPushExecutor} thread
     * pool (see {@code AsyncConfig}) — the RabbitMQ consumer never waits on it.
     */
    @Async("webPushExecutor")
    public void deliverAsync(String recipientUserId, String type, String message) {
        if (pushService == null) {
            return; // not configured — in-app notifications already persisted
        }
        try {
            NotificationPreference preference = preferenceRepository
                    .findByRecipientUserId(recipientUserId).orElse(null);
            if (preference != null && !preference.isWebPushEnabled()) {
                return; // user opted out
            }

            List<PushSubscription> subscriptions =
                    subscriptionRepository.findByRecipientUserId(recipientUserId);
            if (subscriptions.isEmpty()) {
                return; // no browser registered for this user
            }

            String payload = objectMapper.writeValueAsString(
                    Map.of("type", type, "message", message));
            Urgency urgency = "queue.called".equals(type) ? Urgency.HIGH : Urgency.NORMAL;

            for (PushSubscription subscription : subscriptions) {
                sendToSubscription(subscription, payload, urgency);
            }
        } catch (Exception e) {
            // Never propagate: delivery is best-effort by contract.
            log.warn("Web push delivery failed for user {}: {}", recipientUserId, e.getMessage());
        }
    }

    private void sendToSubscription(PushSubscription subscription, String payload, Urgency urgency) {
        try {
            HttpResponse response = pushService.send(Notification.builder()
                    .endpoint(subscription.getEndpoint())
                    .userPublicKey(subscription.getP256dh())
                    .userAuth(subscription.getAuth())
                    .payload(payload)
                    .urgency(urgency)
                    .build());

            int status = response.getStatusLine().getStatusCode();
            if (status == 404 || status == 410) {
                // 404/410 = the subscription is dead (browser unsubscribed or
                // the push service forgot it). Delete it so we stop trying.
                subscriptionRepository.deleteById(subscription.getId());
                log.info("Removed stale push subscription (HTTP {}) for endpoint {}",
                        status, subscription.getEndpoint());
            } else if (status >= 200 && status < 300) {
                log.debug("Push delivered to {} (type pending)", subscription.getEndpoint());
            } else {
                log.warn("Push service returned HTTP {} for endpoint {}",
                        status, subscription.getEndpoint());
            }
        } catch (Exception e) {
            log.warn("Failed to send push to endpoint {}: {}",
                    subscription.getEndpoint(), e.getMessage());
        }
    }

    private static PushService buildPushService(String publicKey, String privateKey, String subject) {
        String pub = normalize(publicKey);
        String priv = normalize(privateKey);
        if (pub.isEmpty() || priv.isEmpty()) {
            log.warn("VAPID keys not configured (VAPID_PUBLIC_KEY/VAPID_PRIVATE_KEY) — "
                    + "Web Push delivery disabled; in-app notifications unaffected");
            return null;
        }
        try {
            PushService service = new PushService(pub, priv, normalize(subject));
            log.info("Web Push delivery enabled (VAPID subject {})", normalize(subject));
            return service;
        } catch (Exception e) {
            // Bad keys must not take the service down — treat like unconfigured.
            log.error("Invalid VAPID keys — Web Push delivery disabled: {}", e.getMessage());
            return null;
        }
    }

    /** Strips all whitespace so PEM-style values pasted with line breaks still work. */
    private static String normalize(String value) {
        return value == null ? "" : value.replaceAll("\\s", "");
    }
}
