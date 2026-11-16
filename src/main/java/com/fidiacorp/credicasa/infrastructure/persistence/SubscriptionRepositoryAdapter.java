package com.fidiacorp.credicasa.infrastructure.persistence;

import com.fidiacorp.credicasa.domain.model.Subscription;
import com.fidiacorp.credicasa.domain.ports.out.SubscriptionRepositoryPort;
import com.fidiacorp.credicasa.infrastructure.persistence.entity.SubscriptionJpaEntity;
import com.fidiacorp.credicasa.infrastructure.persistence.repository.SubscriptionJpaRepository;
import org.springframework.stereotype.Component;

import java.util.Optional;
import java.util.UUID;

@Component
public class SubscriptionRepositoryAdapter implements SubscriptionRepositoryPort {

    private final SubscriptionJpaRepository repository;

    public SubscriptionRepositoryAdapter(SubscriptionJpaRepository repository) {
        this.repository = repository;
    }

    @Override
    public Optional<Subscription> findLatestByAccountId(UUID accountId) {
        return repository.findFirstByAccountIdOrderByCreatedAtDesc(accountId).map(this::toDomain);
    }

    @Override
    public Subscription save(Subscription subscription) {
        SubscriptionJpaEntity entity = toEntity(subscription);
        SubscriptionJpaEntity saved = repository.save(entity);
        return toDomain(saved);
    }

    @Override
    public Optional<Subscription> findById(UUID id) {
        return repository.findById(id).map(this::toDomain);
    }

    private Subscription toDomain(SubscriptionJpaEntity e) {
        return new Subscription(
                e.id,
                e.accountId,
                e.planName,
                e.status,
                e.startDate,
                e.endDate,
                e.autoRenew,
                e.createdAt
        );
    }

    private SubscriptionJpaEntity toEntity(Subscription d) {
        SubscriptionJpaEntity e = new SubscriptionJpaEntity();
        e.id = d.getId();
        e.accountId = d.getAccountId();
        e.planName = d.getPlanName();
        e.status = d.getStatus();
        e.startDate = d.getStartDate();
        e.endDate = d.getEndDate();
        e.autoRenew = d.isAutoRenew();
        e.createdAt = d.getCreatedAt();
        return e;
    }
}
