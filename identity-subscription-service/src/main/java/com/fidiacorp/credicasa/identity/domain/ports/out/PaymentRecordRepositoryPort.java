package com.fidiacorp.credicasa.identity.domain.ports.out;

import com.fidiacorp.credicasa.identity.domain.model.PaymentRecord;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface PaymentRecordRepositoryPort {
    PaymentRecord save(PaymentRecord record);
    Optional<PaymentRecord> findByTransactionReference(String reference);
    List<PaymentRecord> findByAccountId(UUID accountId);
    long countCompletedByAccountId(UUID accountId);
}
