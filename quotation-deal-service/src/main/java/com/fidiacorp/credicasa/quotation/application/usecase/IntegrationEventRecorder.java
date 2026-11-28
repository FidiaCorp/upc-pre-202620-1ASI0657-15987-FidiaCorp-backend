package com.fidiacorp.credicasa.quotation.application.usecase;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fidiacorp.credicasa.quotation.domain.model.OutboxEvent;
import com.fidiacorp.credicasa.quotation.domain.ports.out.OutboxRepositoryPort;
import com.fidiacorp.credicasa.shared.events.IntegrationEvent;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.UUID;

/** Transactional Outbox (ADR-003): el evento se persiste en la MISMA transacción que la oferta o versión. */
@Component
public class IntegrationEventRecorder {
    private final OutboxRepositoryPort outbox;
    private final ObjectMapper json;

    public IntegrationEventRecorder(OutboxRepositoryPort outbox, ObjectMapper json) {
        this.outbox = outbox;
        this.json = json;
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public void record(UUID aggregateId, IntegrationEvent event) {
        try {
            outbox.append(new OutboxEvent(event.eventId(), aggregateId, event.eventType(), json.writeValueAsString(event), Instant.now(),
                    null, 0, null));
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("No se pudo serializar el evento " + event.eventType(), e);
        }
    }
}
