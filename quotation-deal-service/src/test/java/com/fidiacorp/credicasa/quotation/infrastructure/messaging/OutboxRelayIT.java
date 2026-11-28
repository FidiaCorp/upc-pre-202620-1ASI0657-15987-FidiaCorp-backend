package com.fidiacorp.credicasa.quotation.infrastructure.messaging;

import com.fidiacorp.credicasa.quotation.domain.model.OutboxEvent;
import com.fidiacorp.credicasa.quotation.domain.ports.out.OutboxRepositoryPort;
import com.fidiacorp.credicasa.quotation.infrastructure.persistence.repository.OutboxEventJpaRepository;
import com.fidiacorp.credicasa.shared.events.EventTopology;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.amqp.AmqpConnectException;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

/** ADR-003: el relay publica en orden con messageId = eventId, marca publicados y conserva pendientes ante fallo. */
@SpringBootTest(properties = "spring.datasource.url=jdbc:h2:mem:quotation_outbox;MODE=PostgreSQL;DB_CLOSE_DELAY=-1")
class OutboxRelayIT {
    @Autowired OutboxRepositoryPort outbox;
    @Autowired OutboxEventJpaRepository repository;
    RabbitTemplate rabbit;
    OutboxRelay relay;

    @BeforeEach
    void prepare() {
        repository.deleteAll();
        rabbit = mock(RabbitTemplate.class);
        relay = new OutboxRelay(outbox, rabbit, Clock.systemUTC(), 10);
    }

    private OutboxEvent event(String type, Instant at) {
        return outbox.append(new OutboxEvent(UUID.randomUUID(), UUID.randomUUID(), type, "{\"k\":\"" + type + "\"}", at, null, 0, null));
    }

    @Test
    void publishesPendingEventsInOrderWithIdempotencyKeys() {
        OutboxEvent first = event(EventTopology.CREDIT_OFFER_ISSUED, Instant.parse("2026-11-24T10:00:00Z"));
        OutboxEvent second = event(EventTopology.QUOTATION_VERSION_ISSUED, Instant.parse("2026-11-24T10:00:01Z"));
        doNothing().when(rabbit).send(any(String.class), any(String.class), any(Message.class));
        assertThat(relay.publishPending()).isEqualTo(2);
        ArgumentCaptor<Message> messages = ArgumentCaptor.forClass(Message.class);
        ArgumentCaptor<String> keys = ArgumentCaptor.forClass(String.class);
        verify(rabbit, times(2)).send(eq(EventTopology.EXCHANGE), keys.capture(), messages.capture());
        assertThat(keys.getAllValues()).containsExactly(EventTopology.CREDIT_OFFER_ISSUED, EventTopology.QUOTATION_VERSION_ISSUED);
        Message message = messages.getAllValues().get(0);
        assertThat(message.getMessageProperties().getMessageId()).isEqualTo(first.id().toString());
        assertThat((String) message.getMessageProperties().getHeader(EventTopology.HEADER_EVENT_TYPE)).isEqualTo(EventTopology.CREDIT_OFFER_ISSUED);
        assertThat(new String(message.getBody(), StandardCharsets.UTF_8)).contains(EventTopology.CREDIT_OFFER_ISSUED);
        assertThat(outbox.countPending()).isZero();
        assertThat(repository.findById(second.id()).orElseThrow().publishedAt).isNotNull();
        assertThat(relay.publishPending()).isZero();
    }

    @Test
    void brokerOutageKeepsEventsPendingAndRetriesLater() {
        OutboxEvent pending = event(EventTopology.CREDIT_OFFER_ISSUED, Instant.now());
        doThrow(new AmqpConnectException(new java.net.ConnectException("refused"))).when(rabbit).send(any(String.class), any(String.class), any(Message.class));
        assertThat(relay.publishPending()).isZero();
        var stored = repository.findById(pending.id()).orElseThrow();
        assertThat(stored.publishedAt).isNull();
        assertThat(stored.attempts).isEqualTo(1);
        assertThat(stored.lastError).contains("refused");
        doNothing().when(rabbit).send(any(String.class), any(String.class), any(Message.class));
        relay.relay();
        assertThat(outbox.countPending()).isZero();
    }
}
