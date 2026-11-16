package com.fidiacorp.credicasa.domain.model;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Objects;
import java.util.UUID;

public class Subscription {
    private final UUID id;
    private final UUID accountId;
    private final String planName;
    private final SubscriptionStatus status;
    private final LocalDate startDate;
    private final LocalDate endDate;
    private final boolean autoRenew;
    private final LocalDateTime createdAt;

    public Subscription(UUID id, UUID accountId, String planName, SubscriptionStatus status,
                        LocalDate startDate, LocalDate endDate, boolean autoRenew, LocalDateTime createdAt) {
        this.id = Objects.requireNonNull(id, "id must not be null");
        this.accountId = Objects.requireNonNull(accountId, "accountId must not be null");
        this.planName = Objects.requireNonNull(planName, "planName must not be null");
        this.status = Objects.requireNonNull(status, "status must not be null");
        this.startDate = Objects.requireNonNull(startDate, "startDate must not be null");
        this.endDate = Objects.requireNonNull(endDate, "endDate must not be null");
        this.autoRenew = autoRenew;
        this.createdAt = createdAt != null ? createdAt : LocalDateTime.now();
    }

    public UUID getId() { return id; }
    public UUID getAccountId() { return accountId; }
    public String getPlanName() { return planName; }
    public SubscriptionStatus getStatus() { return status; }
    public LocalDate getStartDate() { return startDate; }
    public LocalDate getEndDate() { return endDate; }
    public boolean isAutoRenew() { return autoRenew; }
    public LocalDateTime getCreatedAt() { return createdAt; }

    public boolean isActive() {
        LocalDate today = LocalDate.now();
        return status == SubscriptionStatus.ACTIVE && !today.isBefore(startDate) && !today.isAfter(endDate);
    }
}
