package com.fidiacorp.credicasa.identity.infrastructure.persistence.entity;

import com.fidiacorp.credicasa.identity.domain.model.PaymentStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "payment_records")
public class PaymentRecordJpaEntity {

    @Id
    public UUID id;

    @Column(name = "subscription_id", nullable = false)
    public UUID subscriptionId;

    @Column(name = "account_id", nullable = false)
    public UUID accountId;

    @Column(name = "amount", precision = 12, scale = 2, nullable = false)
    public BigDecimal amount;

    @Column(name = "currency", length = 10, nullable = false)
    public String currency;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", length = 30, nullable = false)
    public PaymentStatus status;

    @Column(name = "transaction_reference", length = 100, nullable = false, unique = true)
    public String transactionReference;

    @Column(name = "payment_method", length = 50, nullable = false)
    public String paymentMethod;

    @Column(name = "processed_at", nullable = false)
    public LocalDateTime processedAt = LocalDateTime.now();

    public PaymentRecordJpaEntity() {}
}
