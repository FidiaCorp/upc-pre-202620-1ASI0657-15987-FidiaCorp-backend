package com.fidiacorp.credicasa.application.dto.request;

import com.fidiacorp.credicasa.domain.model.Currency;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;
import java.util.List;

public record AiRecommendationRequest(
        @NotNull(message = "monthlyIncome is required")
        @DecimalMin(value = "500.00", message = "monthlyIncome must be at least 500.00")
        BigDecimal monthlyIncome,

        BigDecimal downPaymentAvailable,

        Currency currency,

        String preferredDistrict,

        Integer desiredTermYears,

        List<String> preferenceTags
) {}
