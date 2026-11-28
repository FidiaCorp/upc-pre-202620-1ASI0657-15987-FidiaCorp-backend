package com.fidiacorp.credicasa.catalog.application.dto.response;
import com.fidiacorp.credicasa.shared.money.Currency;
import com.fidiacorp.credicasa.shared.json.PlainDecimalSerializer;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;

import java.math.BigDecimal;
import java.util.UUID;

public record PropertyRecommendationItemResponse(
        UUID propertyId,
        String title,
        String address,
        @JsonSerialize(using = PlainDecimalSerializer.class) BigDecimal price,
        Currency currency,
        int relevanceScore,
        boolean financiallyViable,
        @JsonSerialize(using = PlainDecimalSerializer.class) BigDecimal estimatedMonthlyInstallment,
        @JsonSerialize(using = PlainDecimalSerializer.class) BigDecimal debtToIncomeRatioPercent,
        String recommendationReason
) {}
