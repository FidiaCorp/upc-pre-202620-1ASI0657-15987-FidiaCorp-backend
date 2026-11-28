package com.fidiacorp.credicasa.communication.domain.model;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/** Conversación privada entre exactamente dos cuentas (US16), opcionalmente asociada a un inmueble. */
public record Conversation(UUID id, UUID propertyId, UUID participantAId, UUID participantBId, Instant createdAt) {
    public Conversation {
        Objects.requireNonNull(id, "id must not be null");
        Objects.requireNonNull(participantAId, "participantAId must not be null");
        Objects.requireNonNull(participantBId, "participantBId must not be null");
        Objects.requireNonNull(createdAt, "createdAt must not be null");
        if (participantAId.equals(participantBId)) throw new IllegalArgumentException("Una conversación requiere dos cuentas distintas.");
    }

    public boolean isParticipant(UUID accountId) {
        return participantAId.equals(accountId) || participantBId.equals(accountId);
    }

    public UUID counterpartOf(UUID accountId) {
        if (participantAId.equals(accountId)) return participantBId;
        if (participantBId.equals(accountId)) return participantAId;
        throw new IllegalArgumentException("La cuenta no participa en la conversación.");
    }
}
