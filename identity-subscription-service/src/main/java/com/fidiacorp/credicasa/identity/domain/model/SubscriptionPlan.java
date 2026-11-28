package com.fidiacorp.credicasa.identity.domain.model;

import java.math.BigDecimal;
import java.util.Locale;

/** Tarifas oficiales mensuales de CrediCasa (PEN). */
public enum SubscriptionPlan {
    BASIC(new BigDecimal("0.00")),
    PREMIUM(new BigDecimal("49.90")),
    PRO(new BigDecimal("99.90"));

    public static final String CURRENCY = "PEN";
    private final BigDecimal monthlyFee;

    SubscriptionPlan(BigDecimal monthlyFee) { this.monthlyFee = monthlyFee; }

    public BigDecimal monthlyFee() { return monthlyFee; }

    public boolean requiresPayment() { return monthlyFee.signum() > 0; }

    public static SubscriptionPlan parse(String value) {
        if (value == null || value.isBlank()) throw new IllegalArgumentException("planName es obligatorio (BASIC, PREMIUM o PRO).");
        try {
            return valueOf(value.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("Plan no soportado: " + value + ". Use BASIC, PREMIUM o PRO.");
        }
    }
}
