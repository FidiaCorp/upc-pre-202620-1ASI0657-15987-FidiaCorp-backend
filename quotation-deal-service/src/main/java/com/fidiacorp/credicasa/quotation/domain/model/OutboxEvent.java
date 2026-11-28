package com.fidiacorp.credicasa.quotation.domain.model;

import java.time.Instant;
import java.util.UUID;

/** Transactional Outbox: el evento se guarda en la misma transacción que el cambio de negocio y se publica después. */
public record OutboxEvent(UUID id, UUID aggregateId, String eventType, String payload, Instant createdAt, Instant publishedAt,
                          int attempts, String lastError) {
    public boolean published() {
        return publishedAt != null;
    }
}
