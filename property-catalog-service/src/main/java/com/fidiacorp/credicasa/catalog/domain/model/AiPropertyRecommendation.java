package com.fidiacorp.credicasa.catalog.domain.model;
import com.fidiacorp.credicasa.shared.money.Currency;

import java.math.BigDecimal;
import java.util.Objects;
import java.util.UUID;

public class AiPropertyRecommendation {
    private final UUID propertyId;
    private final String title;
    private final String address;
    private final BigDecimal price;
    private final Currency currency;
    private final int relevanceScore;
    private final boolean financiallyViable;
    private final BigDecimal estimatedMonthlyInstallment;
    private final BigDecimal debtToIncomeRatioPercent;
    private final String recommendationReason;

    public AiPropertyRecommendation(UUID propertyId, String title, String address, BigDecimal price,
                                   Currency currency, int relevanceScore, boolean financiallyViable,
                                   BigDecimal estimatedMonthlyInstallment, BigDecimal debtToIncomeRatioPercent,
                                   String recommendationReason) {
        this.propertyId = Objects.requireNonNull(propertyId, "propertyId must not be null");
        this.title = Objects.requireNonNull(title, "title must not be null");
        this.address = Objects.requireNonNull(address, "address must not be null");
        this.price = Objects.requireNonNull(price, "price must not be null");
        this.currency = Objects.requireNonNull(currency, "currency must not be null");
        this.relevanceScore = relevanceScore;
        this.financiallyViable = financiallyViable;
        this.estimatedMonthlyInstallment = estimatedMonthlyInstallment;
        this.debtToIncomeRatioPercent = debtToIncomeRatioPercent;
        this.recommendationReason = Objects.requireNonNull(recommendationReason, "recommendationReason must not be null");
    }

    public UUID getPropertyId() { return propertyId; }
    public String getTitle() { return title; }
    public String getAddress() { return address; }
    public BigDecimal getPrice() { return price; }
    public Currency getCurrency() { return currency; }
    public int getRelevanceScore() { return relevanceScore; }
    public boolean isFinanciallyViable() { return financiallyViable; }
    public BigDecimal getEstimatedMonthlyInstallment() { return estimatedMonthlyInstallment; }
    public BigDecimal getDebtToIncomeRatioPercent() { return debtToIncomeRatioPercent; }
    public String getRecommendationReason() { return recommendationReason; }
}
