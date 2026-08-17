package com.careq.notification.service;

import com.careq.notification.dto.QueueEventDto;
import com.careq.notification.entity.NotificationEntry;
import com.careq.notification.repository.NotificationEntryRepository;
import com.careq.notification.dto.NotificationResponseDto;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Consumer tests — the four known event types must persist a notification with
 * the right recipient, type and composed message; malformed/unknown events are
 * dropped, never persisted .
 */
@ExtendWith(MockitoExtension.class)
class NotificationConsumerTest {

    private static final String PATIENT = "patient-uuid";

    @Mock
    private NotificationEntryRepository repository;

    @Mock
    private WebPushDeliveryService webPushDeliveryService;

    @Mock
    private WebSocketNotifier webSocketNotifier;

    private NotificationConsumer consumer;

    @BeforeEach
    void setUp() {
        consumer = new NotificationConsumer(repository, webPushDeliveryService, webSocketNotifier);
    }

    @Test
    void onQueueEvent_Joined_PersistsNotification() {
        QueueEventDto event = event("queue.joined", 3, "HIGH");

        consumer.onQueueEvent(event);

        NotificationEntry saved = captureSaved();
        assertThat(saved.getRecipientUserId()).isEqualTo(PATIENT);
        assertThat(saved.getType()).isEqualTo("queue.joined");
        assertThat(saved.getMessage()).isEqualTo("You joined Dr. Arjun Sharma's queue — position #3.");
        assertThat(saved.isRead()).isFalse();
    }

    @Test
    void onQueueEvent_Triaged_PersistsNotification() {
        consumer.onQueueEvent(event("queue.triaged", null, "EMERGENCY"));

        NotificationEntry saved = captureSaved();
        assertThat(saved.getType()).isEqualTo("queue.triaged");
        assertThat(saved.getMessage()).isEqualTo("Your triage was updated to EMERGENCY by your doctor.");
    }

    @Test
    void onQueueEvent_Called_PersistsNotification() {
        consumer.onQueueEvent(event("queue.called", null, "NORMAL"));

        NotificationEntry saved = captureSaved();
        assertThat(saved.getType()).isEqualTo("queue.called");
        assertThat(saved.getMessage())
                .isEqualTo("Dr. Arjun Sharma has called you — please head to the consultation room.");
    }

    @Test
    void onQueueEvent_Completed_PersistsNotification() {
        consumer.onQueueEvent(event("queue.completed", null, "NORMAL"));

        NotificationEntry saved = captureSaved();
        assertThat(saved.getType()).isEqualTo("queue.completed");
        assertThat(saved.getMessage()).isEqualTo("Your consultation with Dr. Arjun Sharma is complete — take care!");
    }

    @Test
    void onQueueEvent_MissingDoctorName_FallsBackToYourDoctor() {
        QueueEventDto event = new QueueEventDto("queue.called", PATIENT, 1L, "John", null, "Cardiology",
                1, "NORMAL", LocalDateTime.now());

        consumer.onQueueEvent(event);

        assertThat(captureSaved().getMessage())
                .isEqualTo("your doctor has called you — please head to the consultation room.");
    }

    @Test
    void onQueueEvent_Persisted_AlsoFiresWebPush() {
        consumer.onQueueEvent(event("queue.called", null, "NORMAL"));

        NotificationEntry saved = captureSaved();
        verify(webPushDeliveryService).deliverAsync(
                PATIENT, "queue.called", saved.getMessage());
    }

    @Test
    void onQueueEvent_Persisted_AlsoBroadcastsOverWebSocket() {
        when(repository.save(any(NotificationEntry.class))).thenAnswer(inv -> inv.getArgument(0));
        consumer.onQueueEvent(event("queue.called", null, "NORMAL"));

        ArgumentCaptor<NotificationResponseDto> captor = ArgumentCaptor.forClass(NotificationResponseDto.class);
        verify(webSocketNotifier).notify(eq(PATIENT), captor.capture());
        assertThat(captor.getValue().getType()).isEqualTo("queue.called");
        assertThat(captor.getValue().getMessage()).contains("has called you");
    }

    @Test
    void onQueueEvent_UnknownType_DropsWithoutWebSocketBroadcast() {
        consumer.onQueueEvent(event("queue.unknown", 1, "NORMAL"));

        verify(webSocketNotifier, never()).notify(any(), any());
    }

    @Test
    void onQueueEvent_UnknownType_IsDropped() {
        consumer.onQueueEvent(event("queue.unknown", 1, "NORMAL"));

        verify(repository, never()).save(any(NotificationEntry.class));
        verify(webPushDeliveryService, never()).deliverAsync(any(), any(), any());
    }

    @Test
    void onQueueEvent_NullRecipient_IsDropped() {
        QueueEventDto event = new QueueEventDto("queue.joined", null, 1L, "John", "Dr. X", "Cardiology",
                1, "NORMAL", LocalDateTime.now());

        consumer.onQueueEvent(event);

        verify(repository, never()).save(any(NotificationEntry.class));
        verify(webPushDeliveryService, never()).deliverAsync(any(), any(), any());
    }

    private QueueEventDto event(String type, Integer position, String triage) {
        return new QueueEventDto(type, PATIENT, 1L, "John Patient", "Dr. Arjun Sharma", "Cardiology",
                position, triage, LocalDateTime.of(2026, 8, 10, 9, 0));
    }

    private NotificationEntry captureSaved() {
        ArgumentCaptor<NotificationEntry> captor = ArgumentCaptor.forClass(NotificationEntry.class);
        verify(repository).save(captor.capture());
        return captor.getValue();
    }
}
