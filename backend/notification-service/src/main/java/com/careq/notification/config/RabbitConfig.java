package com.careq.notification.config;

import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.QueueBuilder;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * RabbitMQ consuming configuration for notification-service.
 *
 * <p><b>Topology (mirror of queue-service's publisher side):</b>
 * <pre>
 *   queue-service (publisher)              notification-service (consumer)
 *   ─────────────────────────              ──────────────────────────────
 *   careq.events (topic exchange)  ──▶     careq.notifications (durable queue)
 *     queue.joined / queue.triaged           binding: queue.*  (all four keys)
 *     queue.called  / queue.completed
 * </pre>
 * The exchange is declared here too (idempotent — both sides declare it with
 * the same durable settings), so either service may start first.
 *
 * <p>Events arrive as JSON ({@link com.careq.notification.dto.QueueEventDto})
 * thanks to the {@link Jackson2JsonMessageConverter} — Spring Boot's listener
 * container picks up this {@link MessageConverter} bean automatically.
 */
@Configuration
public class RabbitConfig {

    public static final String EVENTS_EXCHANGE = "careq.events";
    public static final String NOTIFICATION_QUEUE = "careq.notifications";

    @Bean
    public TopicExchange careqEventsExchange() {
        // durable, non-auto-delete — must match queue-service's declaration.
        return new TopicExchange(EVENTS_EXCHANGE, true, false);
    }

    @Bean
    public Queue notificationQueue() {
        return QueueBuilder.durable(NOTIFICATION_QUEUE).build();
    }

    /** One binding with a wildcard covers all four routing keys. */
    @Bean
    public Binding notificationBinding(TopicExchange careqEventsExchange, Queue notificationQueue) {
        return BindingBuilder.bind(notificationQueue).to(careqEventsExchange).with("queue.*");
    }

    @Bean
    public MessageConverter jsonMessageConverter() {
        return new Jackson2JsonMessageConverter();
    }

    /**
     * The consumer listener container factory picks up the {@link MessageConverter}
     * bean automatically, so no explicit {@link RabbitListenerContainerFactory}
     * is needed — the Spring Boot auto-configured factory uses the JSON converter
     * for deserialization.
     */
}
