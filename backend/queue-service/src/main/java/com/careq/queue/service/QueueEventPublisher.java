package com.careq.queue.service;

import com.careq.queue.dto.QueueEventDto;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageBuilder;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

/**
 * publishes post-decision notification events onto the
 * {@code careq.events} topic exchange.
 *
 * <p><b>Non-blocking contract:</b> every publish is wrapped in try/catch. If
 * RabbitMQ is briefly unreachable the event is logged and dropped — a patient
 * joining a queue, being called, or completing a consultation must NEVER fail
 * because a notification couldn't be published. The event is post-decision:
 * it is only sent after the queue entry has already been persisted.
 *
 * <p><b>Serialization:</b> the DTO is serialized to JSON bytes manually via
 * a plain Jackson {@link ObjectMapper} (with {@link JavaTimeModule} for
 * {@code LocalDateTime} support). No AMQP type-ID headers are added, so the
 * consumer (notification-service) deserializes purely from the method parameter
 * type — this avoids FQCN mismatches since both services have their own copy
 * of {@code QueueEventDto} in different packages.
 */
@Service
public class QueueEventPublisher {

    private static final Logger log = LoggerFactory.getLogger(QueueEventPublisher.class);

    public static final String QUEUE_JOINED = "queue.joined";
    public static final String QUEUE_TRIAGED = "queue.triaged";
    public static final String QUEUE_CALLED = "queue.called";
    public static final String QUEUE_COMPLETED = "queue.completed";

    private final RabbitTemplate rabbitTemplate;
    private final ObjectMapper objectMapper;
    private final String exchange;

    public QueueEventPublisher(RabbitTemplate rabbitTemplate,
                               @Value("${queue.events.exchange:careq.events}") String exchange) {
        this.rabbitTemplate = rabbitTemplate;
        this.exchange = exchange;
        this.objectMapper = new ObjectMapper().registerModule(new JavaTimeModule());
    }

    public void publish(String routingKey, QueueEventDto event) {
        try {
            byte[] body = objectMapper.writeValueAsBytes(event);
            Message message = MessageBuilder.withBody(body)
                    .setContentType(MessageProperties.CONTENT_TYPE_JSON)
                    .setContentEncoding("UTF-8")
                    .build();
            rabbitTemplate.send(exchange, routingKey, message);
            log.debug("Published queue event {} for user {}", routingKey, event.getRecipientUserId());
        } catch (Exception e) {
            // Never propagate: the core queue flow must not depend on RabbitMQ.
            log.warn("Failed to publish queue event '{}' for user {} — notification skipped, queue flow unaffected: {}",
                    routingKey, event.getRecipientUserId(), e.getMessage());
        }
    }
}