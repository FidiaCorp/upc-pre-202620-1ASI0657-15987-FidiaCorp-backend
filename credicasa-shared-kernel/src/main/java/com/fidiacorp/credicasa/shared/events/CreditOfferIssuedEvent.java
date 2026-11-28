package com.fidiacorp.credicasa.shared.events;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/** US10 — una oferta crediticia fue emitida para un cliente (versión 1 del contrato). */
public record CreditOfferIssuedEvent(UUID eventId, Instant occurredAt, String correlationId, UUID offerId, UUID recipientId,
                                     UUID issuerId, String bankCode, String notificationTitle, BigDecimal loanAmount,
                                     String currency) implements IntegrationEvent {
    public static final String TYPE = EventTopology.CREDIT_OFFER_ISSUED;

    public CreditOfferIssuedEvent {
        Objects.requireNonNull(eventId, "eventId");
        Objects.requireNonNull(offerId, "offerId");
        Objects.requireNonNull(recipientId, "recipientId");
        if (notificationTitle == null || notificationTitle.isBlank()) {
            throw new IllegalArgumentException("notificationTitle is required");
        }
    }

    @Override
    public String eventType() {
        return TYPE;
    }
}
