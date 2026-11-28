package com.fidiacorp.credicasa.communication.infrastructure.persistence;

import com.fidiacorp.credicasa.communication.domain.model.InboxEntry;
import com.fidiacorp.credicasa.communication.domain.ports.out.InboxRepositoryPort;
import com.fidiacorp.credicasa.communication.infrastructure.persistence.entity.InboxEventJpaEntity;
import com.fidiacorp.credicasa.communication.infrastructure.persistence.repository.InboxEventJpaRepository;
import org.springframework.stereotype.Repository;

import java.util.UUID;

@Repository
public class InboxRepositoryAdapter implements InboxRepositoryPort {
    private final InboxEventJpaRepository repository;

    public InboxRepositoryAdapter(InboxEventJpaRepository repository) {
        this.repository = repository;
    }

    @Override
    public boolean alreadyProcessed(UUID eventId) {
        return repository.existsById(eventId);
    }

    @Override
    public void record(InboxEntry entry) {
        InboxEventJpaEntity e = new InboxEventJpaEntity();
        e.eventId = entry.eventId();
        e.eventType = entry.eventType();
        e.receivedAt = entry.receivedAt();
        repository.saveAndFlush(e);
    }

    @Override
    public long count() {
        return repository.count();
    }
}
