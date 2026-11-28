package com.fidiacorp.credicasa.identity.domain.model;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Objects;
import java.util.UUID;

public class PaymentRecord {
    private final UUID id;
    private final UUID subscriptionId;
    private final UUID accountId;
    private final BigDecimal amount;
    private final String currency;
    private final PaymentStatus status;
    private final String transactionReference;
    private final String paymentMethod;
    private final LocalDateTime processedAt;

    public PaymentRecord(UUID id, UUID subscriptionId, UUID accountId, BigDecimal amount,
                         String currency, PaymentStatus status, String transactionReference,
                         String paymentMethod, LocalDateTime processedAt) {
        this.id = Objects.requireNonNull(id, "id must not be null");
        this.subscriptionId = Objects.requireNonNull(subscriptionId, "subscriptionId must not be null");
        this.accountId = Objects.requireNonNull(accountId, "accountId must not be null");
        this.amount = Objects.requireNonNull(amount, "amount must not be null");
        this.currency = Objects.requireNonNull(currency, "currency must not be null");
        this.status = Objects.requireNonNull(status, "status must not be null");
        this.transactionReference = Objects.requireNonNull(transactionReference, "transactionReference must not be null");
        this.paymentMethod = Objects.requireNonNull(paymentMethod, "paymentMethod must not be null");
        this.processedAt = processedAt != null ? processedAt : LocalDateTime.now();
    }

    public UUID getId() { return id; }
    public UUID getSubscriptionId() { return subscriptionId; }
    public UUID getAccountId() { return accountId; }
    public BigDecimal getAmount() { return amount; }
    public String getCurrency() { return currency; }
    public PaymentStatus getStatus() { return status; }
    public String getTransactionReference() { return transactionReference; }
    public String getPaymentMethod() { return paymentMethod; }
    public LocalDateTime getProcessedAt() { return processedAt; }
}
