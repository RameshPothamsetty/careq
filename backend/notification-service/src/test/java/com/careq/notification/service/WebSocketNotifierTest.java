package com.careq.notification.service;

import com.careq.notification.dto.NotificationResponseDto;
import com.careq.notification.entity.NotificationEntry;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.messaging.simp.SimpMessagingTemplate;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.eq;
import static org.mockito.Mockito.verify;

/**
 * WebSocketNotifier — broadcasts the persisted notification to the
 * recipient's /topic/notifications/{userId} and never throws (fail-open,
 * same contract as Web Push).
 */
@ExtendWith(MockitoExtension.class)
class WebSocketNotifierTest {

    @Mock
    private SimpMessagingTemplate messagingTemplate;

    @Test
    void notify_SendsToUsersTopic() {
        WebSocketNotifier notifier = new WebSocketNotifier(messagingTemplate);
        NotificationResponseDto dto = NotificationResponseDto.fromEntity(
                new NotificationEntry("user-1", "queue.called", "Dr. X has called you"));

        notifier.notify("user-1", dto);

        verify(messagingTemplate).convertAndSend("/topic/notifications/user-1", dto);
    }

    @Test
    void notify_BrokerFailure_IsSwallowed() {
        WebSocketNotifier notifier = new WebSocketNotifier(messagingTemplate);
        doThrow(new IllegalStateException("broker down"))
                .when(messagingTemplate)
                .convertAndSend(eq("/topic/notifications/user-1"), org.mockito.ArgumentMatchers.any(Object.class));

        assertThatCode(() -> notifier.notify("user-1",
                NotificationResponseDto.fromEntity(new NotificationEntry("user-1", "queue.joined", "joined"))))
                .doesNotThrowAnyException();
    }
}
