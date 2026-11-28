package com.fidiacorp.credicasa.catalog.domain.model;
import com.fidiacorp.credicasa.shared.money.Currency;

import java.math.BigDecimal;
import java.util.List;
import java.util.Locale;
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
        if (monthlyIncome.signum() <= 0) throw new IllegalArgumentException("monthlyIncome must be positive");
        this.downPaymentAvailable = downPaymentAvailable == null ? BigDecimal.ZERO : downPaymentAvailable.max(BigDecimal.ZERO);
        this.currency = currency != null ? currency : Currency.PEN;
        this.preferredDistrict = preferredDistrict != null ? preferredDistrict.trim().toLowerCase(Locale.ROOT) : "";
        this.desiredTermYears = desiredTermYears > 0 && desiredTermYears <= 30 ? desiredTermYears : 20;
        this.preferenceTags = preferenceTags != null ? List.copyOf(preferenceTags.stream().filter(Objects::nonNull).toList()) : List.of();
    }

    public BigDecimal getMonthlyIncome() { return monthlyIncome; }
    public BigDecimal getDownPaymentAvailable() { return downPaymentAvailable; }
    public Currency getCurrency() { return currency; }
    public String getPreferredDistrict() { return preferredDistrict; }
    public int getDesiredTermYears() { return desiredTermYears; }
    public List<String> getPreferenceTags() { return preferenceTags; }
}
