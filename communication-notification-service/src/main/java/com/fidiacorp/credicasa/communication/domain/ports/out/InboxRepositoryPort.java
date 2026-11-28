package com.fidiacorp.credicasa.communication.domain.ports.out;

import com.fidiacorp.credicasa.communication.domain.model.InboxEntry;

import java.util.UUID;

public interface InboxRepositoryPort {
    boolean alreadyProcessed(UUID eventId);
    void record(InboxEntry entry);
    long count();
}
