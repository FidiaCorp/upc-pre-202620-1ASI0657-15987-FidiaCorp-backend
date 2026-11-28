package com.fidiacorp.credicasa.identity.application.dto.response;
import com.fidiacorp.credicasa.shared.json.PlainDecimalSerializer;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fidiacorp.credicasa.identity.domain.model.PaymentStatus;
import com.fidiacorp.credicasa.identity.domain.model.SubscriptionStatus;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

public record PaymentVerificationResponse(
        UUID paymentId,
        UUID subscriptionId,
        String transactionReference,
        @JsonSerialize(using = PlainDecimalSerializer.class) BigDecimal amount,
        String currency,
        String paymentMethod,
        String planName,
        PaymentStatus paymentStatus,
        SubscriptionStatus subscriptionStatus,
        LocalDate endDate,
        LocalDateTime processedAt,
        String message
) {}
