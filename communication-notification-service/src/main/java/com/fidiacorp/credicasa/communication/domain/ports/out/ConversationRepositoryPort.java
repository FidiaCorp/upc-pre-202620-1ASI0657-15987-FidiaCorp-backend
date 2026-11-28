package com.fidiacorp.credicasa.communication.domain.ports.out;

import com.fidiacorp.credicasa.communication.domain.model.Conversation;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ConversationRepositoryPort {
    Conversation save(Conversation conversation);
    Optional<Conversation> findById(UUID id);
    /** Busca la conversación existente entre dos cuentas (en cualquier orden) para el mismo inmueble (o sin inmueble). */
    Optional<Conversation> findBetween(UUID accountA, UUID accountB, UUID propertyId);
    List<Conversation> findByParticipant(UUID accountId);
}
