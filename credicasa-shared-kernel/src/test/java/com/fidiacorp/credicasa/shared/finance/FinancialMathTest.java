package com.fidiacorp.credicasa.shared.finance;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class FinancialMathTest {
    @Test
    void monthlyRateCompoundsBackToTheAnnualRate() {
        BigDecimal tem = FinancialMath.monthlyRateFromAnnualEffective(new BigDecimal("0.12"));
        assertThat(tem.scale()).isEqualTo(24);
        BigDecimal annual = BigDecimal.ONE.add(tem).pow(12, FinancialMath.MC).subtract(BigDecimal.ONE);
        assertThat(annual.setScale(12, FinancialMath.ROUNDING)).isEqualByComparingTo("0.120000000000");
        assertThat(FinancialMath.monthlyRateFromAnnualEffective(BigDecimal.ZERO)).isEqualByComparingTo("0");
        assertThat(FinancialMath.monthlyRateFromAnnualEffective(BigDecimal.ONE)).isGreaterThan(new BigDecimal("0.0594"));
    }

    @Test
    void rejectsInvalidEffectiveRates() {
        assertThatThrownBy(() -> FinancialMath.monthlyRateFromAnnualEffective(null)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> FinancialMath.monthlyRateFromAnnualEffective(new BigDecimal("-0.01"))).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> FinancialMath.monthlyRateFromAnnualEffective(new BigDecimal("1.01"))).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void convertsNominalToEffectiveAnnualRate() {
        // TNA 12 % capitalizable mensualmente → TEA 12.682503 %
        assertThat(FinancialMath.effectiveAnnualFromNominal(new BigDecimal("0.12"), 12)).isEqualByComparingTo("0.12682503");
        // capitalización diaria (360) → 12.747431 %
        assertThat(FinancialMath.effectiveAnnualFromNominal(new BigDecimal("0.12"), 360)).isEqualByComparingTo("0.12747431");
        assertThat(FinancialMath.effectiveAnnualFromNominal(new BigDecimal("0.10"), 1)).isEqualByComparingTo("0.10000000");
    }

    @Test
    void rejectsInvalidNominalInputs() {
        assertThatThrownBy(() -> FinancialMath.effectiveAnnualFromNominal(null, 12)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> FinancialMath.effectiveAnnualFromNominal(BigDecimal.ZERO, 12)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> FinancialMath.effectiveAnnualFromNominal(new BigDecimal("1.5"), 12)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> FinancialMath.effectiveAnnualFromNominal(new BigDecimal("0.1"), 0)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> FinancialMath.effectiveAnnualFromNominal(new BigDecimal("0.1"), 366)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void levelInstallmentMatchesFrenchFormula() {
        BigDecimal tem = FinancialMath.monthlyRateFromAnnualEffective(new BigDecimal("0.10"));
        BigDecimal installment = FinancialMath.levelInstallment(new BigDecimal("100000"), tem, 240);
        assertThat(installment).isEqualByComparingTo("936.64");
        assertThat(FinancialMath.levelInstallment(new BigDecimal("1200"), BigDecimal.ZERO, 12)).isEqualByComparingTo("100.00");
        assertThat(FinancialMath.levelInstallment(BigDecimal.ZERO, tem, 12)).isEqualByComparingTo("0.00");
        assertThat(FinancialMath.levelInstallment(null, tem, 12)).isEqualByComparingTo("0.00");
        assertThatThrownBy(() -> FinancialMath.levelInstallment(BigDecimal.TEN, tem, 0)).isInstanceOf(IllegalArgumentException.class);
        assertThat(FinancialMath.money(new BigDecimal("2.345"))).isEqualByComparingTo("2.34");
    }
}
