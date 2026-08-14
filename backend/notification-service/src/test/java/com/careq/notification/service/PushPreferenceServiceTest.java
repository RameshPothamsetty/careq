package com.careq.notification.service;

import com.careq.notification.dto.NotificationPreferenceResponseDto;
import com.careq.notification.dto.PushSubscriptionRequestDto;
import com.careq.notification.entity.NotificationPreference;
import com.careq.notification.entity.PushSubscription;
import com.careq.notification.repository.NotificationPreferenceRepository;
import com.careq.notification.repository.PushSubscriptionRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Day 16 — preference defaults-on semantics and endpoint-upsert subscription
 * registration.
 */
@ExtendWith(MockitoExtension.class)
class PushPreferenceServiceTest {

    private static final String PATIENT = "patient-uuid";
    private static final String ENDPOINT = "https://fcm.googleapis.com/fcm/send/abc";

    @Mock
    private NotificationPreferenceRepository preferenceRepository;
    @Mock
    private PushSubscriptionRepository subscriptionRepository;

    private PushPreferenceService service;

    @BeforeEach
    void setUp() {
        service = new PushPreferenceService(preferenceRepository, subscriptionRepository);
    }

    @Test
    void getPreferences_NoRow_DefaultsToEnabled() {
        when(preferenceRepository.findByRecipientUserId(PATIENT)).thenReturn(Optional.empty());

        NotificationPreferenceResponseDto prefs = service.getPreferences(PATIENT);

        assertThat(prefs.isWebPushEnabled()).isTrue();
    }

    @Test
    void getPreferences_ExistingRow_Returned() {
        when(preferenceRepository.findByRecipientUserId(PATIENT))
                .thenReturn(Optional.of(new NotificationPreference(PATIENT, false)));

        assertThat(service.getPreferences(PATIENT).isWebPushEnabled()).isFalse();
    }

    @Test
    void updatePreferences_CreatesRowWhenMissing() {
        when(preferenceRepository.findByRecipientUserId(PATIENT)).thenReturn(Optional.empty());
        when(preferenceRepository.save(any(NotificationPreference.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        service.updatePreferences(PATIENT, false);

        ArgumentCaptor<NotificationPreference> captor = ArgumentCaptor.forClass(NotificationPreference.class);
        verify(preferenceRepository).save(captor.capture());
        assertThat(captor.getValue().getRecipientUserId()).isEqualTo(PATIENT);
        assertThat(captor.getValue().isWebPushEnabled()).isFalse();
    }

    @Test
    void updatePreferences_ExistingRow_Updated() {
        NotificationPreference existing = new NotificationPreference(PATIENT, true);
        when(preferenceRepository.findByRecipientUserId(PATIENT)).thenReturn(Optional.of(existing));
        when(preferenceRepository.save(any(NotificationPreference.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        NotificationPreferenceResponseDto result = service.updatePreferences(PATIENT, false);

        assertThat(existing.isWebPushEnabled()).isFalse();
        assertThat(result.isWebPushEnabled()).isFalse();
    }

    @Test
    void registerSubscription_NewEndpoint_Inserts() {
        when(subscriptionRepository.findByEndpoint(ENDPOINT)).thenReturn(Optional.empty());

        service.registerSubscription(PATIENT, request());

        ArgumentCaptor<PushSubscription> captor = ArgumentCaptor.forClass(PushSubscription.class);
        verify(subscriptionRepository).save(captor.capture());
        assertThat(captor.getValue().getRecipientUserId()).isEqualTo(PATIENT);
        assertThat(captor.getValue().getEndpoint()).isEqualTo(ENDPOINT);
        assertThat(captor.getValue().getP256dh()).isEqualTo("p256dh-base64url");
        assertThat(captor.getValue().getAuth()).isEqualTo("auth-base64url");
    }

    @Test
    void registerSubscription_SameEndpoint_UpsertsToCaller() {
        PushSubscription existing = new PushSubscription("other-user", ENDPOINT, "old-p256dh", "old-auth");
        when(subscriptionRepository.findByEndpoint(ENDPOINT)).thenReturn(Optional.of(existing));

        service.registerSubscription(PATIENT, request());

        // The row is re-assigned + refreshed, never duplicated.
        assertThat(existing.getRecipientUserId()).isEqualTo(PATIENT);
        assertThat(existing.getP256dh()).isEqualTo("p256dh-base64url");
        assertThat(existing.getAuth()).isEqualTo("auth-base64url");
        verify(subscriptionRepository).save(existing);
    }

    @Test
    void unregisterSubscription_BlankEndpoint_IsNoOp() {
        service.unregisterSubscription(PATIENT, "   ");

        verify(subscriptionRepository, never()).deleteByEndpointAndRecipientUserId(any(), any());
    }

    @Test
    void unregisterSubscription_RemovesOwnRow() {
        service.unregisterSubscription(PATIENT, ENDPOINT);

        verify(subscriptionRepository).deleteByEndpointAndRecipientUserId(ENDPOINT, PATIENT);
    }

    private PushSubscriptionRequestDto request() {
        return new PushSubscriptionRequestDto(ENDPOINT,
                new PushSubscriptionRequestDto.Keys("p256dh-base64url", "auth-base64url"));
    }
}
