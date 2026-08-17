package com.careq.notification.service;

import com.careq.notification.dto.NotificationResponseDto;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;

/**
 * Pushes a persisted notification to the recipient's real-time topic
 * ({@code /topic/notifications/{userId}}), consumed by the open frontend tab
 * via the STOMP socket at {@code /ws}.
 *
 * <p>Fail-open by design (same contract as Web Push): a broadcast to a topic
 * with no subscribers is a harmless no-op, and any infrastructure hiccup is
 * logged and swallowed — the persisted row + polling remain the source of
 * truth, so a missed push can never lose a notification.
 */
@Service
public class WebSocketNotifier {

    private static final Logger log = LoggerFactory.getLogger(WebSocketNotifier.class);

    private static final String TOPIC_PREFIX = "/topic/notifications/";

    private final SimpMessagingTemplate messagingTemplate;

    public WebSocketNotifier(SimpMessagingTemplate messagingTemplate) {
        this.messagingTemplate = messagingTemplate;
    }

    /**
     * Broadcasts one notification to the recipient's topic. Never throws.
     */
    public void notify(String recipientUserId, NotificationResponseDto notification) {
        try {
            messagingTemplate.convertAndSend(TOPIC_PREFIX + recipientUserId, notification);
        } catch (Exception e) {
            log.warn("WebSocket push failed for user {} — notification stays in the bell: {}",
                    recipientUserId, e.getMessage());
        }
    }
}
