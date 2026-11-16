package com.fidiacorp.credicasa.application.dto.response;

import java.math.BigDecimal;
import java.util.List;

public record AiRecommendationResponse(
        BigDecimal monthlyIncome,
        BigDecimal maxAllowableInstallment,
        String evaluationSummary,
        int totalAnalyzed,
        List<PropertyRecommendationItemResponse> recommendations
) {}
