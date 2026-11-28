package com.fidiacorp.credicasa.communication.infrastructure.persistence;

import com.fidiacorp.credicasa.communication.domain.model.Conversation;
import com.fidiacorp.credicasa.communication.domain.ports.out.ConversationRepositoryPort;
import com.fidiacorp.credicasa.communication.infrastructure.persistence.entity.ConversationJpaEntity;
import com.fidiacorp.credicasa.communication.infrastructure.persistence.repository.ConversationJpaRepository;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Component
public class ConversationRepositoryAdapter implements ConversationRepositoryPort {
    private final ConversationJpaRepository repository;

    public ConversationRepositoryAdapter(ConversationJpaRepository repository) {
        this.repository = repository;
    }

    @Override
    public Conversation save(Conversation c) {
        ConversationJpaEntity e = new ConversationJpaEntity();
        e.id = c.id();
        e.propertyId = c.propertyId();
        e.participantAId = c.participantAId();
        e.participantBId = c.participantBId();
        e.createdAt = c.createdAt();
        return map(repository.saveAndFlush(e));
    }

    @Override
    public Optional<Conversation> findById(UUID id) {
        return repository.findById(id).map(this::map);
    }

    @Override
    public Optional<Conversation> findBetween(UUID a, UUID b, UUID propertyId) {
        List<ConversationJpaEntity> found = propertyId == null
                ? repository.findBetweenWithoutProperty(a, b)
                : repository.findBetweenForProperty(a, b, propertyId);
        return found.stream().findFirst().map(this::map);
    }

    @Override
    public List<Conversation> findByParticipant(UUID accountId) {
        return repository.findByParticipant(accountId).stream().map(this::map).toList();
    }

    private Conversation map(ConversationJpaEntity e) {
        return new Conversation(e.id, e.propertyId, e.participantAId, e.participantBId, e.createdAt);
    }
}
