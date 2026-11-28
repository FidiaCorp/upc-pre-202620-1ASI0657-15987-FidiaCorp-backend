package com.fidiacorp.credicasa.catalog.application.dto.response;
import com.fidiacorp.credicasa.shared.money.Currency;
import com.fidiacorp.credicasa.shared.json.PlainDecimalSerializer;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;

import java.math.BigDecimal;
import java.util.List;

public record AiRecommendationResponse(
        @JsonSerialize(using = PlainDecimalSerializer.class) BigDecimal monthlyIncome,
        Currency currency,
        @JsonSerialize(using = PlainDecimalSerializer.class) BigDecimal maxAllowableInstallment,
        String evaluationSummary,
        int totalAnalyzed,
        List<PropertyRecommendationItemResponse> recommendations
) {}
