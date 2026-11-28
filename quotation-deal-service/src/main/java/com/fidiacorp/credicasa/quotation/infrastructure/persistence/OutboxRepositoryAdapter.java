package com.fidiacorp.credicasa.quotation.infrastructure.persistence;

import com.fidiacorp.credicasa.quotation.domain.model.OutboxEvent;
import com.fidiacorp.credicasa.quotation.domain.ports.out.OutboxRepositoryPort;
import com.fidiacorp.credicasa.quotation.infrastructure.persistence.entity.OutboxEventJpaEntity;
import com.fidiacorp.credicasa.quotation.infrastructure.persistence.repository.OutboxEventJpaRepository;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Repository
public class OutboxRepositoryAdapter implements OutboxRepositoryPort {
    private final OutboxEventJpaRepository repository;

    public OutboxRepositoryAdapter(OutboxEventJpaRepository repository) {
        this.repository = repository;
    }

    @Override
    public OutboxEvent append(OutboxEvent event) {
        OutboxEventJpaEntity e = new OutboxEventJpaEntity();
        e.id = event.id(); e.aggregateId = event.aggregateId(); e.eventType = event.eventType(); e.payload = event.payload();
        e.createdAt = event.createdAt(); e.publishedAt = event.publishedAt(); e.attempts = event.attempts(); e.lastError = event.lastError();
        return map(repository.saveAndFlush(e));
    }

    @Override
    @Transactional(readOnly = true)
    public List<OutboxEvent> findPending(int limit) {
        return repository.findPending(PageRequest.of(0, limit)).stream().map(this::map).toList();
    }

    @Override
    @Transactional
    public void markPublished(UUID id, Instant at) {
        repository.findById(id).ifPresent(e -> {
            e.publishedAt = at;
            e.attempts++;
            e.lastError = null;
        });
    }

    @Override
    @Transactional
    public void markFailed(UUID id, String error) {
        repository.findById(id).ifPresent(e -> {
            e.attempts++;
            e.lastError = error == null ? null : error.substring(0, Math.min(500, error.length()));
        });
    }

    @Override
    @Transactional(readOnly = true)
    public long countPending() {
        return repository.countByPublishedAtIsNull();
    }

    private OutboxEvent map(OutboxEventJpaEntity e) {
        return new OutboxEvent(e.id, e.aggregateId, e.eventType, e.payload, e.createdAt, e.publishedAt, e.attempts, e.lastError);
    }
}
