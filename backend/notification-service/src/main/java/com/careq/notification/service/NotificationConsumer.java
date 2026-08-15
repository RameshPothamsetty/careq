package com.careq.notification.service;

import com.careq.notification.config.RabbitConfig;
import com.careq.notification.dto.QueueEventDto;
import com.careq.notification.entity.NotificationEntry;
import com.careq.notification.repository.NotificationEntryRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

/**
 * consumes queue events from the {@code careq.events} topic exchange
 * (bound via {@code queue.*}) and persists one {@link NotificationEntry} per
 * event. The message copy lives here so queue-service stays lean and the
 * wording is owned by the notification layer.
 */
@Component
public class NotificationConsumer {

    private static final Logger log = LoggerFactory.getLogger(NotificationConsumer.class);

    private final NotificationEntryRepository repository;
    private final WebPushDeliveryService webPushDeliveryService;

    public NotificationConsumer(NotificationEntryRepository repository,
                                WebPushDeliveryService webPushDeliveryService) {
        this.repository = repository;
        this.webPushDeliveryService = webPushDeliveryService;
    }

    @RabbitListener(queues = RabbitConfig.NOTIFICATION_QUEUE)
    public void onQueueEvent(QueueEventDto event) {
        if (event == null || event.getRecipientUserId() == null || event.getRecipientUserId().isBlank()) {
            log.warn("Dropping queue event without a recipient: {}", event);
            return;
        }

        String message = buildMessage(event);
        if (message == null) {
            log.warn("Dropping unknown queue event type '{}' for user {}", event.getEventType(), event.getRecipientUserId());
            return;
        }

        try {
            // The message column is VARCHAR(500) and doctorName can be up to
            // 255 chars — guard against a DataIntegrityViolation at the DB.
            repository.save(new NotificationEntry(event.getRecipientUserId(), event.getEventType(), truncate(message, 500)));
            log.debug("Persisted notification type={} for user {}", event.getEventType(), event.getRecipientUserId());

            // fire-and-forget Web Push. The in-app row is the source
            // of truth; delivery is async, best-effort and never blocks this
            // consumer (see WebPushDeliveryService for the fail-open contract).
            webPushDeliveryService.deliverAsync(event.getRecipientUserId(), event.getEventType(), message);
        } catch (Exception e) {
            // Never redeliver-loop on a poisoned message (Spring AMQP's default
            // is to requeue a throwing listener forever, blocking the queue).
            // Ack + log: a lost notification is far cheaper than a stuck queue.
            log.error("Failed to persist notification type={} for user {} — dropping event: {}",
                    event.getEventType(), event.getRecipientUserId(), e.getMessage());
        }
    }

    private static String truncate(String value, int maxLength) {
        return value.length() <= maxLength ? value : value.substring(0, maxLength);
    }

    /**
     * Composes the human-readable sentence for the four known event types.
     * Returns null for anything unrecognized (defensive — the binding already
     * restricts delivery to {@code queue.*}).
     */
    String buildMessage(QueueEventDto event) {
        String doctor = event.getDoctorName() == null || event.getDoctorName().isBlank() ? "your doctor" : event.getDoctorName();
        return switch (event.getEventType()) {
            case "queue.joined" -> {
                String position = event.getPosition() == null ? "" : " — position #" + event.getPosition();
                yield "You joined " + doctor + "'s queue" + position + ".";
            }
            case "queue.triaged" -> {
                String level = event.getTriageLevel() == null ? "updated" : event.getTriageLevel();
                yield "Your triage was updated to " + level + " by your doctor.";
            }
            case "queue.called" -> doctor + " has called you — please head to the consultation room.";
            case "queue.completed" -> "Your consultation with " + doctor + " is complete — take care!";
            default -> null;
        };
    }
}
