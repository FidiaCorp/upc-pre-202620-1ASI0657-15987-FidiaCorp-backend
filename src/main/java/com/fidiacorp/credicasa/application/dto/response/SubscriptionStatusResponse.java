package com.fidiacorp.credicasa.application.dto.response;

import com.fidiacorp.credicasa.domain.model.SubscriptionStatus;

import java.time.LocalDate;
import java.util.UUID;

public record SubscriptionStatusResponse(
        UUID subscriptionId,
        UUID accountId,
        String planName,
        SubscriptionStatus status,
        boolean active,
        LocalDate startDate,
        LocalDate endDate,
        boolean autoRenew,
        String statusDescription
) {}
