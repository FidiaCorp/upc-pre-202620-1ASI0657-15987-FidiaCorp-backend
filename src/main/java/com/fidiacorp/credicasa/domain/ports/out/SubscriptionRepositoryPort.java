package com.fidiacorp.credicasa.domain.ports.out;

import com.fidiacorp.credicasa.domain.model.Subscription;

import java.util.Optional;
import java.util.UUID;

public interface SubscriptionRepositoryPort {
    Optional<Subscription> findLatestByAccountId(UUID accountId);
    Subscription save(Subscription subscription);
    Optional<Subscription> findById(UUID id);
}
