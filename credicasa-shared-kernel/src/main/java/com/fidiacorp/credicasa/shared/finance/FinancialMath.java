package com.fidiacorp.credicasa.shared.finance;

import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;

/**
 * Matemática financiera determinista compartida (DECIMAL128, HALF_EVEN; sin double ni Math.pow):
 * TEA → TEM, TNA → TEA (US19) y cuota nivelada del método francés.
 */
public final class FinancialMath {
    public static final MathContext MC = MathContext.DECIMAL128;
    public static final RoundingMode ROUNDING = RoundingMode.HALF_EVEN;
    private static final BigDecimal CONVERGENCE = new BigDecimal("1e-32");
    private static final BigDecimal TWELVE = BigDecimal.valueOf(12);

    private FinancialMath() {
    }

    /** Redondeo monetario a céntimos. */
    public static BigDecimal money(BigDecimal value) {
        return value.setScale(2, ROUNDING);
    }

    /** TEM = (1 + TEA)^(1/12) − 1, por Newton–Raphson en DECIMAL128 (escala 24). */
    public static BigDecimal monthlyRateFromAnnualEffective(BigDecimal tea) {
        if (tea == null || tea.signum() < 0 || tea.compareTo(BigDecimal.ONE) > 0) {
            throw new IllegalArgumentException("TEA inválida.");
        }
        if (tea.signum() == 0) {
            return BigDecimal.ZERO.setScale(24);
        }
        BigDecimal value = BigDecimal.ONE.add(tea);
        BigDecimal root = BigDecimal.ONE;
        for (int i = 0; i < 100; i++) {
            BigDecimal next = root.multiply(BigDecimal.valueOf(11), MC)
                    .add(value.divide(root.pow(11, MC), MC), MC).divide(TWELVE, MC);
            boolean converged = next.subtract(root).abs().compareTo(CONVERGENCE) <= 0;
            root = next;
            if (converged) {
                break;
            }
        }
        return root.subtract(BigDecimal.ONE, MC).setScale(24, ROUNDING);
    }

    /**
     * US19 — TEA = (1 + TNA/m)^m − 1 con m periodos de capitalización por año (p. ej. 12 o 360).
     * El resultado conserva 8 decimales, como las TEA de entrada del motor.
     */
    public static BigDecimal effectiveAnnualFromNominal(BigDecimal tna, int compoundingPeriodsPerYear) {
        if (tna == null || tna.signum() <= 0 || tna.compareTo(BigDecimal.ONE) > 0) {
            throw new IllegalArgumentException("TNA inválida: debe ser un decimal en (0, 1].");
        }
        if (compoundingPeriodsPerYear < 1 || compoundingPeriodsPerYear > 365) {
            throw new IllegalArgumentException("La capitalización debe estar entre 1 y 365 periodos por año.");
        }
        BigDecimal periodic = tna.divide(BigDecimal.valueOf(compoundingPeriodsPerYear), MC);
        return BigDecimal.ONE.add(periodic, MC).pow(compoundingPeriodsPerYear, MC).subtract(BigDecimal.ONE, MC).setScale(8, ROUNDING);
    }

    /** Cuota nivelada (sin balón) a céntimos: P / ((1 − (1+i)^−n) / i); con i = 0, P / n. */
    public static BigDecimal levelInstallment(BigDecimal principal, BigDecimal monthlyRate, int months) {
        if (months <= 0) {
            throw new IllegalArgumentException("El plazo debe ser mayor que cero.");
        }
        if (principal == null || principal.signum() <= 0) {
            return money(BigDecimal.ZERO);
        }
        BigDecimal capital = money(principal);
        if (monthlyRate.signum() == 0) {
            return money(capital.divide(BigDecimal.valueOf(months), MC));
        }
        BigDecimal future = BigDecimal.ONE.add(monthlyRate, MC).pow(months, MC);
        BigDecimal annuity = BigDecimal.ONE.subtract(BigDecimal.ONE.divide(future, MC), MC).divide(monthlyRate, MC);
        return money(capital.divide(annuity, MC));
    }
}
