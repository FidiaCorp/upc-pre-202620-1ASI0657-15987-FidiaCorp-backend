package com.fidiacorp.credicasa.application.dto.response;

import com.fidiacorp.credicasa.domain.model.Currency;

import java.math.BigDecimal;
import java.util.UUID;

public record PropertyRecommendationItemResponse(
        UUID propertyId,
        String title,
        String address,
        BigDecimal price,
        Currency currency,
        int relevanceScore,
        boolean financiallyViable,
        BigDecimal estimatedMonthlyInstallment,
        BigDecimal debtToIncomeRatioPercent,
        String recommendationReason
) {}
