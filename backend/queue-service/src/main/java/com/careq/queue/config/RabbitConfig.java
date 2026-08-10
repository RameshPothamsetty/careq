package com.careq.queue.config;

import org.springframework.amqp.core.TopicExchange;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Day 13 — RabbitMQ publishing configuration for queue-service.
 *
 * <p><b>Topology:</b> one durable <em>topic</em> exchange {@code careq.events}.
 * queue-service only declares the exchange and PUBLISHES — the binding of the
 * durable queue {@code careq.notifications} with routing keys
 * {@code queue.joined|queue.triaged|queue.called|queue.completed} is owned by
 * notification-service (the consumer), so the topology stays single-writer /
 * single-reader and either service can start independently.
 *
 * <p><b>Non-blocking contract:</b> publishing happens AFTER the decision is
 * persisted and is wrapped in try/catch by {@code QueueEventPublisher} — a
 * RabbitMQ outage never fails a join / call-next / complete.
 *
 * <p><b>Serialization:</b> events travel as JSON (a shared {@code QueueEventDto}
 * shape duplicated in both services — this codebase has no shared module).
 * The {@link Jackson2JsonMessageConverter} bean is injected directly into
 * {@code QueueEventPublisher} which converts the DTO explicitly via
 * {@code toMessage()} and publishes with {@code send()}.
 */
@Configuration
public class RabbitConfig {

    public static final String EVENTS_EXCHANGE = "careq.events";

    @Bean
    public TopicExchange careqEventsExchange() {
        // durable, non-auto-delete — the exchange survives broker restarts.
        return new TopicExchange(EVENTS_EXCHANGE, true, false);
    }

    @Bean
    public MessageConverter jsonMessageConverter() {
        return new Jackson2JsonMessageConverter();
    }
}