package com.fidiacorp.credicasa.shared.money;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Objects;

/** Importe monetario inmutable a céntimo (escala 2, HALF_EVEN). Operaciones solo entre la misma moneda. */
public record Money(BigDecimal amount, Currency currency) implements Comparable<Money> {
    public static final RoundingMode ROUNDING = RoundingMode.HALF_EVEN;

    public Money {
        Objects.requireNonNull(amount, "amount must not be null");
        Objects.requireNonNull(currency, "currency must not be null");
        amount = amount.setScale(2, ROUNDING);
    }

    public static Money of(String amount, Currency currency) {
        return new Money(new BigDecimal(amount), currency);
    }

    public static Money zero(Currency currency) {
        return new Money(BigDecimal.ZERO, currency);
    }

    public Money plus(Money other) {
        return new Money(amount.add(sameCurrency(other).amount), currency);
    }

    public Money minus(Money other) {
        return new Money(amount.subtract(sameCurrency(other).amount), currency);
    }

    public Money times(BigDecimal factor) {
        return new Money(amount.multiply(Objects.requireNonNull(factor, "factor must not be null")), currency);
    }

    public boolean isPositive() {
        return amount.signum() > 0;
    }

    public boolean isNegative() {
        return amount.signum() < 0;
    }

    public String toPlainString() {
        return amount.toPlainString();
    }

    @Override
    public int compareTo(Money other) {
        return amount.compareTo(sameCurrency(other).amount);
    }

    private Money sameCurrency(Money other) {
        Objects.requireNonNull(other, "other must not be null");
        if (other.currency != currency) {
            throw new IllegalArgumentException("No se pueden combinar importes en " + currency + " y " + other.currency + ".");
        }
        return other;
    }
}
