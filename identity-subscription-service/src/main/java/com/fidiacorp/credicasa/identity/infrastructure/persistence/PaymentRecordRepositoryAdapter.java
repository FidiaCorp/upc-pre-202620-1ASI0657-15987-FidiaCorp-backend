package com.fidiacorp.credicasa.identity.infrastructure.persistence;

import com.fidiacorp.credicasa.identity.domain.model.PaymentRecord;
import com.fidiacorp.credicasa.identity.domain.ports.out.PaymentRecordRepositoryPort;
import com.fidiacorp.credicasa.identity.infrastructure.persistence.entity.PaymentRecordJpaEntity;
import com.fidiacorp.credicasa.identity.infrastructure.persistence.repository.PaymentRecordJpaRepository;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Component
public class PaymentRecordRepositoryAdapter implements PaymentRecordRepositoryPort {

    private final PaymentRecordJpaRepository repository;

    public PaymentRecordRepositoryAdapter(PaymentRecordJpaRepository repository) {
        this.repository = repository;
    }

    @Override
    public PaymentRecord save(PaymentRecord record) {
        PaymentRecordJpaEntity entity = toEntity(record);
        PaymentRecordJpaEntity saved = repository.save(entity);
        return toDomain(saved);
    }

    @Override
    public Optional<PaymentRecord> findByTransactionReference(String reference) {
        return repository.findByTransactionReference(reference).map(this::toDomain);
    }

    @Override
    public List<PaymentRecord> findByAccountId(UUID accountId) {
        return repository.findByAccountIdOrderByProcessedAtDesc(accountId)
                .stream().map(this::toDomain).toList();
    }

    @Override
    public long countCompletedByAccountId(UUID accountId) {
        return repository.countByAccountIdAndStatus(accountId, com.fidiacorp.credicasa.identity.domain.model.PaymentStatus.COMPLETED);
    }

    private PaymentRecord toDomain(PaymentRecordJpaEntity e) {
        return new PaymentRecord(
                e.id,
                e.subscriptionId,
                e.accountId,
                e.amount,
                e.currency,
                e.status,
                e.transactionReference,
                e.paymentMethod,
                e.processedAt
        );
    }

    private PaymentRecordJpaEntity toEntity(PaymentRecord d) {
        PaymentRecordJpaEntity e = new PaymentRecordJpaEntity();
        e.id = d.getId();
        e.subscriptionId = d.getSubscriptionId();
        e.accountId = d.getAccountId();
        e.amount = d.getAmount();
        e.currency = d.getCurrency();
        e.status = d.getStatus();
        e.transactionReference = d.getTransactionReference();
        e.paymentMethod = d.getPaymentMethod();
        e.processedAt = d.getProcessedAt();
        return e;
    }
}
