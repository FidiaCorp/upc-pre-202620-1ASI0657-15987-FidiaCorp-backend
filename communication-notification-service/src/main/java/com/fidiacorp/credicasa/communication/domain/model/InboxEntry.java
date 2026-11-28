package com.fidiacorp.credicasa.communication.domain.model;

import java.time.Instant;
import java.util.UUID;

/** Idempotent Inbox (ADR-003): un evento de integración se procesa como máximo una vez por {@code eventId}. */
public record InboxEntry(UUID eventId, String eventType, Instant receivedAt) {
}
