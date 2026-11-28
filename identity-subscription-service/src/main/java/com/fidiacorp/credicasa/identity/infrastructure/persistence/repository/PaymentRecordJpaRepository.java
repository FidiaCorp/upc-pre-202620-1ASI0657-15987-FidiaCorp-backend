package com.fidiacorp.credicasa.identity.infrastructure.persistence.repository;

import com.fidiacorp.credicasa.identity.infrastructure.persistence.entity.PaymentRecordJpaEntity;
import com.fidiacorp.credicasa.identity.domain.model.PaymentStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface PaymentRecordJpaRepository extends JpaRepository<PaymentRecordJpaEntity, UUID> {
    Optional<PaymentRecordJpaEntity> findByTransactionReference(String transactionReference);
    List<PaymentRecordJpaEntity> findByAccountIdOrderByProcessedAtDesc(UUID accountId);
    long countByAccountIdAndStatus(UUID accountId, PaymentStatus status);
}
