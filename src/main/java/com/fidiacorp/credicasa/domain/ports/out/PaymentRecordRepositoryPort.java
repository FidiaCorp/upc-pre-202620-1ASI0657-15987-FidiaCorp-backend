package com.fidiacorp.credicasa.domain.ports.out;

import com.fidiacorp.credicasa.domain.model.PaymentRecord;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface PaymentRecordRepositoryPort {
    PaymentRecord save(PaymentRecord record);
    Optional<PaymentRecord> findByTransactionReference(String reference);
    List<PaymentRecord> findByAccountId(UUID accountId);
}
