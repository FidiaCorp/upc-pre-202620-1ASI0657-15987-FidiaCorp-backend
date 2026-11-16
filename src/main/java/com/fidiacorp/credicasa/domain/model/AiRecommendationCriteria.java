package com.fidiacorp.credicasa.domain.model;

import java.math.BigDecimal;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

public class AiRecommendationCriteria {
    private final BigDecimal monthlyIncome;
    private final BigDecimal downPaymentAvailable;
    private final Currency currency;
    private final String preferredDistrict;
    private final int desiredTermYears;
    private final List<String> preferenceTags;

    public AiRecommendationCriteria(BigDecimal monthlyIncome, BigDecimal downPaymentAvailable,
                                    Currency currency, String preferredDistrict,
                                    int desiredTermYears, List<String> preferenceTags) {
        this.monthlyIncome = Objects.requireNonNull(monthlyIncome, "monthlyIncome must not be null");
        this.downPaymentAvailable = downPaymentAvailable != null ? downPaymentAvailable : BigDecimal.ZERO;
        this.currency = currency != null ? currency : Currency.PEN;
        this.preferredDistrict = preferredDistrict != null ? preferredDistrict.trim().toLowerCase() : "";
        this.desiredTermYears = desiredTermYears > 0 ? desiredTermYears : 20;
        this.preferenceTags = preferenceTags != null ? preferenceTags : Collections.emptyList();
    }

    public BigDecimal getMonthlyIncome() { return monthlyIncome; }
    public BigDecimal getDownPaymentAvailable() { return downPaymentAvailable; }
    public Currency getCurrency() { return currency; }
    public String getPreferredDistrict() { return preferredDistrict; }
    public int getDesiredTermYears() { return desiredTermYears; }
    public List<String> getPreferenceTags() { return preferenceTags; }
}
