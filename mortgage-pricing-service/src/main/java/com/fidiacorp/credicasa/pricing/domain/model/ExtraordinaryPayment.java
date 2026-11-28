package com.fidiacorp.credicasa.pricing.domain.model;

import java.math.BigDecimal;
import java.util.Objects;

/** US20: prepago opcional aplicado junto con la cuota {@code afterInstallment} de un escenario. */
public record ExtraordinaryPayment(int afterInstallment, BigDecimal amount, PrepaymentStrategy strategy) {
    public ExtraordinaryPayment {
        Objects.requireNonNull(amount, "amount");
        Objects.requireNonNull(strategy, "strategy");
        if (afterInstallment < 1) throw new IllegalArgumentException("El prepago debe aplicarse después de una cuota (>= 1).");
        if (amount.signum() <= 0 || Math.max(0, amount.stripTrailingZeros().scale()) > 2) {
            throw new IllegalArgumentException("El prepago debe ser un importe positivo con hasta 2 decimales.");
        }
    }
}
