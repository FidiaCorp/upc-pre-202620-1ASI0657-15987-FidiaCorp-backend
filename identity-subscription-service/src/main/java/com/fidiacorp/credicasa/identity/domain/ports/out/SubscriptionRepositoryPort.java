package com.fidiacorp.credicasa.identity.domain.ports.out;

import com.fidiacorp.credicasa.identity.domain.model.Subscription;

import java.util.Optional;
import java.util.UUID;

public interface SubscriptionRepositoryPort {
    Optional<Subscription> findLatestByAccountId(UUID accountId);
    Subscription save(Subscription subscription);
    Optional<Subscription> findById(UUID id);
}
