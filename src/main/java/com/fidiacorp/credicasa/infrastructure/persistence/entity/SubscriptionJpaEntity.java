package com.fidiacorp.credicasa.infrastructure.persistence.entity;

import com.fidiacorp.credicasa.domain.model.SubscriptionStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "subscriptions")
public class SubscriptionJpaEntity {

    @Id
    public UUID id;

    @Column(name = "account_id", nullable = false)
    public UUID accountId;

    @Column(name = "plan_name", length = 50, nullable = false)
    public String planName;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", length = 30, nullable = false)
    public SubscriptionStatus status;

    @Column(name = "start_date", nullable = false)
    public LocalDate startDate;

    @Column(name = "end_date", nullable = false)
    public LocalDate endDate;

    @Column(name = "auto_renew", nullable = false)
    public boolean autoRenew = true;

    @Column(name = "created_at", nullable = false)
    public LocalDateTime createdAt = LocalDateTime.now();

    @Column(name = "updated_at", nullable = false)
    public LocalDateTime updatedAt = LocalDateTime.now();

    public SubscriptionJpaEntity() {}
}
