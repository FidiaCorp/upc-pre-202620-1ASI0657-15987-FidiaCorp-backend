package com.fidiacorp.credicasa.shared.events;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/** US21 — se congeló una nueva versión inmutable de la cotización de una oferta (versión 1 del contrato). */
public record QuotationVersionIssuedEvent(UUID eventId, Instant occurredAt, String correlationId, UUID offerId, UUID versionId,
                                          int versionNumber, UUID recipientId, String snapshotSha256,
                                          String notificationTitle) implements IntegrationEvent {
    public static final String TYPE = EventTopology.QUOTATION_VERSION_ISSUED;

    public QuotationVersionIssuedEvent {
        Objects.requireNonNull(eventId, "eventId");
        Objects.requireNonNull(offerId, "offerId");
        Objects.requireNonNull(versionId, "versionId");
        Objects.requireNonNull(recipientId, "recipientId");
        if (versionNumber < 1) throw new IllegalArgumentException("versionNumber must be >= 1");
        if (snapshotSha256 == null || !snapshotSha256.matches("[0-9a-f]{64}")) {
            throw new IllegalArgumentException("snapshotSha256 must be 64 lowercase hex chars");
        }
        if (notificationTitle == null || notificationTitle.isBlank()) {
            throw new IllegalArgumentException("notificationTitle is required");
        }
    }

    @Override
    public String eventType() {
        return TYPE;
    }
}
