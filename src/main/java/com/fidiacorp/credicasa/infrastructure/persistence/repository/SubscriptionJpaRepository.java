package com.fidiacorp.credicasa.infrastructure.persistence.repository;

import com.fidiacorp.credicasa.infrastructure.persistence.entity.SubscriptionJpaEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface SubscriptionJpaRepository extends JpaRepository<SubscriptionJpaEntity, UUID> {
    Optional<SubscriptionJpaEntity> findFirstByAccountIdOrderByCreatedAtDesc(UUID accountId);
}
