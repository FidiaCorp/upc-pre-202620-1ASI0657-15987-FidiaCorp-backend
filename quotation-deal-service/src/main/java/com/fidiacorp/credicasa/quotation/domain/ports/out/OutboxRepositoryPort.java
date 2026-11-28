package com.fidiacorp.credicasa.quotation.domain.ports.out;

import com.fidiacorp.credicasa.quotation.domain.model.OutboxEvent;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public interface OutboxRepositoryPort {
    OutboxEvent append(OutboxEvent event);
    List<OutboxEvent> findPending(int limit);
    void markPublished(UUID id, Instant at);
    void markFailed(UUID id, String error);
    long countPending();
}
