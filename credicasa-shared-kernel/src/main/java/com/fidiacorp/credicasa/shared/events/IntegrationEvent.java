package com.fidiacorp.credicasa.shared.events;

import java.time.Instant;
import java.util.UUID;

/** Contrato mínimo de un evento de integración versionado (publicado vía Transactional Outbox). */
public interface IntegrationEvent {
    UUID eventId();

    Instant occurredAt();

    String correlationId();

    /** Clave de enrutamiento estable, p. ej. {@code credit.offer.issued.v1}. */
    String eventType();

    /** Destinatario de la notificación generada por el evento. */
    UUID recipientId();
}
