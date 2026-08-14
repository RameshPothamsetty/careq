package com.careq.notification.service;

import com.careq.notification.entity.NotificationPreference;
import com.careq.notification.entity.PushSubscription;
import com.careq.notification.repository.NotificationPreferenceRepository;
import com.careq.notification.repository.PushSubscriptionRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import nl.martijndwars.webpush.Notification;
import nl.martijndwars.webpush.PushService;
import nl.martijndwars.webpush.Utils;
import org.apache.http.HttpResponse;
import org.apache.http.StatusLine;
import org.bouncycastle.jce.ECNamedCurveTable;
import org.bouncycastle.jce.interfaces.ECPublicKey;
import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.SecureRandom;
import java.security.Security;
import java.util.Base64;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Day 16 — delivery rules: preference opt-out is honored, missing keys /
 * subscriptions short-circuit, dead subscriptions (404/410) are deleted, and
 * every failure is swallowed (fail-open contract — delivery must never throw).
 */
@ExtendWith(MockitoExtension.class)
class WebPushDeliveryServiceTest {

    private static final String PATIENT = "patient-uuid";

    @Mock
    private PushSubscriptionRepository subscriptionRepository;
    @Mock
    private NotificationPreferenceRepository preferenceRepository;
    @Mock
    private PushService pushService;
    @Mock
    private HttpResponse response;
    @Mock
    private StatusLine statusLine;

    /** Real P-256 keys — the Notification builder parses p256dh eagerly, so fake strings throw. */
    private static final String P256DH;
    private static final String AUTH;

    static {
        try {
            Security.addProvider(new BouncyCastleProvider());
            KeyPairGenerator generator = KeyPairGenerator.getInstance("ECDH", "BC");
            generator.initialize(ECNamedCurveTable.getParameterSpec("prime256v1"));
            KeyPair pair = generator.generateKeyPair();
            P256DH = Base64.getUrlEncoder().withoutPadding()
                    .encodeToString(Utils.encode((ECPublicKey) pair.getPublic()));
            byte[] auth = new byte[16];
            new SecureRandom().nextBytes(auth);
            AUTH = Base64.getUrlEncoder().withoutPadding().encodeToString(auth);
        } catch (Exception e) {
            throw new ExceptionInInitializerError(e);
        }
    }

    private WebPushDeliveryService service;

    @BeforeEach
    void setUp() {
        service = new WebPushDeliveryService(subscriptionRepository, preferenceRepository,
                new ObjectMapper(), pushService);
    }

    private PushSubscription subscription() {
        PushSubscription subscription = new PushSubscription(PATIENT, "https://fcm.googleapis.com/fcm/send/abc",
                P256DH, AUTH);
        ReflectionTestUtils.setField(subscription, "id", 42L);
        return subscription;
    }

    @Test
    void deliver_NoSubscription_SendsNothing() throws Exception {
        when(preferenceRepository.findByRecipientUserId(PATIENT)).thenReturn(Optional.empty());
        when(subscriptionRepository.findByRecipientUserId(PATIENT)).thenReturn(List.of());

        service.deliverAsync(PATIENT, "queue.called", "Dr. X has called you.");

        verify(pushService, never()).send(any(Notification.class));
    }

    @Test
    void deliver_UserOptedOut_SendsNothing() throws Exception {
        when(preferenceRepository.findByRecipientUserId(PATIENT))
                .thenReturn(Optional.of(new NotificationPreference(PATIENT, false)));

        service.deliverAsync(PATIENT, "queue.called", "Dr. X has called you.");

        verify(pushService, never()).send(any(Notification.class));
        verify(subscriptionRepository, never()).findByRecipientUserId(any());
    }

    @Test
    void deliver_Enabled_SendsEncryptedPayloadWithHighUrgencyForCalled() throws Exception {
        when(preferenceRepository.findByRecipientUserId(PATIENT)).thenReturn(Optional.empty());
        when(subscriptionRepository.findByRecipientUserId(PATIENT)).thenReturn(List.of(subscription()));
        when(statusLine.getStatusCode()).thenReturn(200);
        when(response.getStatusLine()).thenReturn(statusLine);
        when(pushService.send(any(Notification.class))).thenReturn(response);

        service.deliverAsync(PATIENT, "queue.called", "Dr. X has called you.");

        ArgumentCaptor<Notification> captor = ArgumentCaptor.forClass(Notification.class);
        verify(pushService).send(captor.capture());
        Notification notification = captor.getValue();
        assertThat(notification.getEndpoint()).isEqualTo(subscription().getEndpoint());
        assertThat(notification.hasPayload()).isTrue();
        assertThat(notification.getUrgency()).isEqualTo(nl.martijndwars.webpush.Urgency.HIGH);
        assertThat(new String(notification.getPayload())).contains("queue.called");
        // 200 → subscription kept
        verify(subscriptionRepository, never()).deleteById(any());
    }

    @Test
    void deliver_StaleSubscription_IsDeleted() throws Exception {
        PushSubscription stale = subscription();
        when(preferenceRepository.findByRecipientUserId(PATIENT)).thenReturn(Optional.empty());
        when(subscriptionRepository.findByRecipientUserId(PATIENT)).thenReturn(List.of(stale));
        when(statusLine.getStatusCode()).thenReturn(410);
        when(response.getStatusLine()).thenReturn(statusLine);
        when(pushService.send(any(Notification.class))).thenReturn(response);

        service.deliverAsync(PATIENT, "queue.joined", "You joined a queue.");

        verify(subscriptionRepository).deleteById(stale.getId());
    }

    @Test
    void deliver_PushServiceFailure_IsSwallowed() throws Exception {
        when(preferenceRepository.findByRecipientUserId(PATIENT)).thenReturn(Optional.empty());
        when(subscriptionRepository.findByRecipientUserId(PATIENT)).thenReturn(List.of(subscription()));
        when(pushService.send(any(Notification.class)))
                .thenThrow(new RuntimeException("push service unreachable"));

        // Must not throw — the consumer's ack path and in-app flow stay safe.
        service.deliverAsync(PATIENT, "queue.completed", "All done.");

        verify(subscriptionRepository, never()).deleteById(any());
    }

    @Test
    void deliver_NoKeysConfigured_SendsNothing() {
        // pushService == null (constructor with null) — same as unconfigured VAPID.
        WebPushDeliveryService disabled = new WebPushDeliveryService(
                subscriptionRepository, preferenceRepository, new ObjectMapper(), null);

        disabled.deliverAsync(PATIENT, "queue.called", "Dr. X has called you.");

        verify(preferenceRepository, never()).findByRecipientUserId(any());
        verify(subscriptionRepository, never()).findByRecipientUserId(any());
    }
}
