package com.fidiacorp.credicasa.application.dto.response;

import com.fidiacorp.credicasa.domain.model.PaymentStatus;
import com.fidiacorp.credicasa.domain.model.SubscriptionStatus;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

public record PaymentVerificationResponse(
        UUID paymentId,
        UUID subscriptionId,
        String transactionReference,
        BigDecimal amount,
        String currency,
        PaymentStatus paymentStatus,
        SubscriptionStatus subscriptionStatus,
        LocalDateTime processedAt,
        String message
) {}
