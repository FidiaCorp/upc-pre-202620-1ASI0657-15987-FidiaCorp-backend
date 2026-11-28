package com.fidiacorp.credicasa.quotation.infrastructure.messaging;

import com.fidiacorp.credicasa.quotation.domain.model.OutboxEvent;
import com.fidiacorp.credicasa.quotation.domain.ports.out.OutboxRepositoryPort;
import com.fidiacorp.credicasa.shared.events.EventTopology;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageBuilder;
import org.springframework.amqp.core.MessageDeliveryMode;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.time.Clock;

/**
 * Relay del Transactional Outbox (ADR-003): publica en orden los eventos pendientes en {@code credicasa.events} con
 * {@code messageId = eventId} (el consumidor deduplica por ese id en {@code inbox_events}) y los marca publicados.
 * Entrega al-menos-una-vez: si RabbitMQ no está disponible el evento permanece pendiente y se reintenta.
 */
@Component
@ConditionalOnProperty(name = "app.outbox.relay.enabled", havingValue = "true", matchIfMissing = true)
public class OutboxRelay {
    private static final Logger log = LoggerFactory.getLogger(OutboxRelay.class);
    private final OutboxRepositoryPort outbox;
    private final RabbitTemplate rabbit;
    private final Clock clock;
    private final int batchSize;

    public OutboxRelay(OutboxRepositoryPort outbox, RabbitTemplate rabbit, Clock clock, @Value("${app.outbox.relay.batch-size:50}") int batchSize) {
        this.outbox = outbox;
        this.rabbit = rabbit;
        this.clock = clock;
        this.batchSize = batchSize;
    }

    @Scheduled(fixedDelayString = "${app.outbox.relay.interval-ms:1000}", initialDelayString = "${app.outbox.relay.initial-delay-ms:2000}")
    public void relay() {
        publishPending();
    }

    public int publishPending() {
        int published = 0;
        for (OutboxEvent event : outbox.findPending(batchSize)) {
            try {
                rabbit.send(EventTopology.EXCHANGE, event.eventType(), toMessage(event));
                outbox.markPublished(event.id(), clock.instant());
                published++;
            } catch (RuntimeException e) {
                outbox.markFailed(event.id(), e.toString());
                log.warn("[OUTBOX] Evento {} ({}) pendiente: {}", event.id(), event.eventType(), e.toString());
                break;
            }
        }
        return published;
    }

    static Message toMessage(OutboxEvent event) {
        return MessageBuilder.withBody(event.payload().getBytes(StandardCharsets.UTF_8))
                .setContentType(MessageProperties.CONTENT_TYPE_JSON).setContentEncoding("UTF-8")
                .setMessageId(event.id().toString()).setDeliveryMode(MessageDeliveryMode.PERSISTENT)
                .setHeader(EventTopology.HEADER_EVENT_TYPE, event.eventType()).setHeader(EventTopology.HEADER_EVENT_ID, event.id().toString())
                .build();
    }
}
