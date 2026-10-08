package com.fidiacorp.credicasa.domain.model;

import java.math.BigDecimal;
import java.util.Objects;

public class BalloonPayment {

    private final int installmentNumber;
    private final BigDecimal amount;

    public BalloonPayment(int installmentNumber, BigDecimal amount) {
        this.installmentNumber = installmentNumber;
        this.amount = amount != null ? amount : BigDecimal.ZERO;
    }

    public static BalloonPayment none() {
        return new BalloonPayment(0, BigDecimal.ZERO);
    }

    public static BalloonPayment of(int installmentNumber, BigDecimal amount) {
        return new BalloonPayment(installmentNumber, amount);
    }

    public boolean isConfigured() {
        return amount.compareTo(BigDecimal.ZERO) > 0 && installmentNumber > 0;
    }

    public int getInstallmentNumber() {
        return installmentNumber;
    }

    public BigDecimal getAmount() {
        return amount;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        BalloonPayment that = (BalloonPayment) o;
        return installmentNumber == that.installmentNumber && Objects.equals(amount, that.amount);
    }

    @Override
    public int hashCode() {
        return Objects.hash(installmentNumber, amount);
    }
}
