package com.fidiacorp.credicasa.infrastructure.persistence.repository;

import com.fidiacorp.credicasa.infrastructure.persistence.entity.PaymentRecordJpaEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface PaymentRecordJpaRepository extends JpaRepository<PaymentRecordJpaEntity, UUID> {
    Optional<PaymentRecordJpaEntity> findByTransactionReference(String transactionReference);
    List<PaymentRecordJpaEntity> findByAccountIdOrderByProcessedAtDesc(UUID accountId);
}
