package com.careq.queue.service;

import com.careq.queue.dto.QueueEventDto;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.amqp.AmqpConnectException;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.core.RabbitTemplate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;

/**
 * the non-blocking publish contract. A RabbitMQ outage (connect
 * refused, broker down) must be logged and swallowed by the publisher, never
 * propagated into the queue flow that called it.
 *
 * <p>The publisher serializes the DTO to JSON bytes with a plain Jackson
 * ObjectMapper, builds the {@link Message} manually, and calls
 * {@code send()} — no AMQP type-ID headers are added.
 */
@ExtendWith(MockitoExtension.class)
class QueueEventPublisherTest {

    @Mock
    private RabbitTemplate rabbitTemplate;

    @Captor
    private ArgumentCaptor<Message> messageCaptor;

    @Test
    void publish_BrokerUnreachable_DoesNotThrow() {
        QueueEventPublisher publisher = new QueueEventPublisher(rabbitTemplate, "careq.events");
        doThrow(new AmqpConnectException(new java.net.ConnectException("connection refused")))
                .when(rabbitTemplate).send(anyString(), anyString(), any(Message.class));

        assertThatCode(() -> publisher.publish("queue.joined", event()))
                .doesNotThrowAnyException();
    }

    @Test
    void publish_Success_SendsJsonMessage() {
        QueueEventPublisher publisher = new QueueEventPublisher(rabbitTemplate, "careq.events");
        QueueEventDto event = event();

        publisher.publish("queue.called", event);

        verify(rabbitTemplate).send(eq("careq.events"), eq("queue.called"), messageCaptor.capture());
        Message sent = messageCaptor.getValue();
        assertThat(sent.getMessageProperties().getContentType()).isEqualTo("application/json");
        assertThat(sent.getBody()).isNotEmpty();
        // Body should contain serialized fields
        assertThat(new String(sent.getBody())).contains("Dr. Arjun Sharma");
    }

    private QueueEventDto event() {
        return new QueueEventDto("queue.joined", "patient-uuid", 1L, "John Patient",
                "Dr. Arjun Sharma", "Cardiology", 2, "HIGH", null);
    }
}