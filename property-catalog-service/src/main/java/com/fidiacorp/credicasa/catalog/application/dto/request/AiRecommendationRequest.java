package com.fidiacorp.credicasa.catalog.application.dto.request;
import com.fidiacorp.credicasa.shared.money.Currency;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.util.List;

/** Contrato US15. downPaymentAvailable y preferredDistrict son opcionales (null → 0.00 / sin filtro). */
public record AiRecommendationRequest(
        @NotNull(message = "monthlyIncome is required")
        @DecimalMin(value = "500.00", message = "monthlyIncome must be at least 500.00")
        BigDecimal monthlyIncome,

        @DecimalMin(value = "0.00", message = "downPaymentAvailable must not be negative")
        BigDecimal downPaymentAvailable,

        Currency currency,

        @Size(max = 60, message = "preferredDistrict must not exceed 60 characters")
        String preferredDistrict,

        @Min(value = 1, message = "desiredTermYears must be between 1 and 30")
        @Max(value = 30, message = "desiredTermYears must be between 1 and 30")
        Integer desiredTermYears,

        @Size(max = 20, message = "preferenceTags admits up to 20 tags")
        List<String> preferenceTags
) {}
