package com.fidiacorp.credicasa.pricing.domain.service;

import com.fidiacorp.credicasa.pricing.domain.model.BalloonPayment;
import com.fidiacorp.credicasa.pricing.domain.model.ExtraordinaryPayment;
import com.fidiacorp.credicasa.pricing.domain.model.GracePeriod;
import com.fidiacorp.credicasa.pricing.domain.model.GracePeriodType;
import com.fidiacorp.credicasa.pricing.domain.model.Installment;
import com.fidiacorp.credicasa.pricing.domain.model.PrepaymentStrategy;
import com.fidiacorp.credicasa.pricing.domain.model.PropertySnapshot;
import com.fidiacorp.credicasa.pricing.domain.model.RateTerms;
import com.fidiacorp.credicasa.pricing.domain.model.RateType;
import com.fidiacorp.credicasa.pricing.domain.ports.in.CalculateScheduleCommand;
import com.fidiacorp.credicasa.shared.money.Currency;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** US19/US20: conversión TNA→TEA y comparador de escenarios con prepago (dominio puro). */
class ScenarioComparisonEngineTest {
    private final FinancialMetricsCalculator metrics = new FinancialMetricsCalculator();
    private final AmortizationStrategy french = new FrenchAmortizationEngine(metrics);
    private final ScenarioComparisonEngine engine = new ScenarioComparisonEngine(french, metrics);

    private CalculateScheduleCommand command(String tea, int months, Currency currency, GracePeriod grace, BalloonPayment balloon) {
        return new CalculateScheduleCommand(new BigDecimal("200000.00"), currency, months, new BigDecimal(tea), grace, balloon, null, null,
                new BigDecimal("250000.00"), null, LocalDate.of(2026, 1, 15), null,
                new PropertySnapshot(UUID.randomUUID(), new BigDecimal("250000.00"), currency, "Lima"), null, UUID.randomUUID());
    }

    private ScenarioComparisonEngine.ScenarioInput input(String label, String tea, int months, ExtraordinaryPayment prepay) {
        return new ScenarioComparisonEngine.ScenarioInput(label, command(tea, months, Currency.PEN, GracePeriod.none(), BalloonPayment.none()),
                RateTerms.effective(new BigDecimal(tea)), prepay);
    }

    @Test
    @DisplayName("US19: TNA 12% mensual/diaria → TEA exacta y AmortizationStrategy expone el método francés")
    void nominalRatesConvertToEffective() {
        RateTerms monthly = RateTerms.nominal(new BigDecimal("0.12"), 12);
        assertThat(monthly.rateType()).isEqualTo(RateType.TNA);
        assertThat(monthly.effectiveAnnualRate()).isEqualByComparingTo("0.12682503");
        assertThat(RateTerms.nominal(new BigDecimal("0.12"), 360).effectiveAnnualRate()).isEqualByComparingTo("0.12747431");
        assertThat(RateTerms.restore(null, null, null, new BigDecimal("0.09")).rateType()).isEqualTo(RateType.TEA);
        assertThat(RateTerms.restore("TNA", new BigDecimal("0.12"), 12, new BigDecimal("0.12682503")).compoundingPeriodsPerYear()).isEqualTo(12);
        assertThatThrownBy(() -> new RateTerms(RateType.TNA, BigDecimal.ONE, null, BigDecimal.ONE)).isInstanceOf(IllegalArgumentException.class);
        // Columnas NUMERIC(60,24): la restauración normaliza a la misma representación que el cálculo.
        RateTerms restored = RateTerms.restore("TNA", new BigDecimal("0.120000000000000000000000"), 360, new BigDecimal("0.127474310000000000000000"));
        assertThat(restored.inputRate().toPlainString()).isEqualTo("0.12");
        assertThat(restored.effectiveAnnualRate().toPlainString()).isEqualTo("0.12747431");
        RateTerms legacy = RateTerms.restore(null, null, null, new BigDecimal("0.081234560000000000000000"));
        assertThat(legacy.rateType()).isEqualTo(RateType.TEA);
        assertThat(legacy.effectiveAnnualRate().toPlainString()).isEqualTo("0.08123456");
        assertThat(RateTerms.plainRate(new BigDecimal("1E+2")).toPlainString()).isEqualTo("100");
        assertThat(RateTerms.plainRate(null)).isNull();
        assertThat(RateTerms.canonicalRate(null)).isNull();
        assertThat(french.method()).isEqualTo("FRENCH");
    }

    @Test
    @DisplayName("US20: la menor TEA gana en TCEA, intereses, cuota, VAN y costo total")
    void ranksScenariosSideBySide() {
        var result = engine.compare(List.of(input("Banco A", "0.095", 240, null), input("Banco B", "0.085", 240, null)));
        assertThat(result.scenarios()).hasSize(2);
        assertThat(result.bestByTcea()).isEqualTo("Banco B");
        assertThat(result.bestByTotalInterest()).isEqualTo("Banco B");
        assertThat(result.bestByMonthlyInstallment()).isEqualTo("Banco B");
        assertThat(result.bestByVan()).isEqualTo("Banco B");
        assertThat(result.bestByTotalCost()).isEqualTo("Banco B");
        assertThat(result.scenarios().get(0).interestSavings()).isEqualByComparingTo("0.00");
    }

    @Test
    @DisplayName("US20: prepago REDUCE_TERM acorta el plazo y REDUCE_INSTALLMENT baja la cuota, ambos liquidan en 0.00")
    void prepaymentStrategies() {
        var term = new ExtraordinaryPayment(24, new BigDecimal("50000.00"), PrepaymentStrategy.REDUCE_TERM);
        var installment = new ExtraordinaryPayment(24, new BigDecimal("50000.00"), PrepaymentStrategy.REDUCE_INSTALLMENT);
        var result = engine.compare(List.of(input("Sin prepago", "0.09", 240, null), input("Plazo", "0.09", 240, term),
                input("Cuota", "0.09", 240, installment)));
        var base = result.scenarios().get(0);
        var shorter = result.scenarios().get(1);
        var lower = result.scenarios().get(2);
        assertThat(shorter.effectiveTermMonths()).isLessThan(240);
        assertThat(lower.effectiveTermMonths()).isEqualTo(240);
        for (var outcome : List.of(shorter, lower)) {
            List<Installment> rows = outcome.schedule();
            assertThat(rows.get(rows.size() - 1).getFinalBalance()).isEqualByComparingTo("0.00");
            assertThat(rows.get(23).getTotalPayment()).isGreaterThan(new BigDecimal("50000.00"));
            assertThat(outcome.interestSavings()).isPositive();
            for (int i = 0; i < rows.size(); i++) assertThat(rows.get(i).getInstallmentNumber()).isEqualTo(i + 1);
        }
        assertThat(lower.schedule().get(30).getInstallmentAmount()).isLessThan(base.schedule().get(30).getInstallmentAmount());
        assertThat(shorter.interestSavings()).isGreaterThan(lower.interestSavings());
        assertThat(result.bestByTotalInterest()).isEqualTo("Plazo");
    }

    @Test
    @DisplayName("US20: un prepago igual al saldo liquida el crédito en esa cuota")
    void fullPayoff() {
        var base = french.generateQuotation(command("0.09", 24, Currency.PEN, GracePeriod.none(), BalloonPayment.none()));
        BigDecimal balance = base.getSchedule().get(11).getFinalBalance();
        var result = engine.compare(List.of(input("Base", "0.09", 24, null),
                input("Liquidar", "0.09", 24, new ExtraordinaryPayment(12, balance, PrepaymentStrategy.REDUCE_TERM))));
        assertThat(result.scenarios().get(1).effectiveTermMonths()).isEqualTo(12);
    }

    @Test
    @DisplayName("US20: reglas de comparación y prepago inválidos")
    void rejectsInvalidComparisons() {
        assertThatThrownBy(() -> engine.compare(List.of(input("Solo", "0.09", 24, null)))).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> engine.compare(null)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> engine.compare(List.of(input("A", "0.09", 24, null), input("A", "0.08", 24, null))))
                .hasMessageContaining("etiqueta");
        assertThatThrownBy(() -> engine.compare(List.of(input(" ", "0.09", 24, null), input("B", "0.08", 24, null))))
                .hasMessageContaining("etiqueta");
        var usd = new ScenarioComparisonEngine.ScenarioInput("USD", command("0.07", 24, Currency.USD, GracePeriod.none(), BalloonPayment.none()),
                RateTerms.effective(new BigDecimal("0.07")), null);
        assertThatThrownBy(() -> engine.compare(List.of(input("PEN", "0.09", 24, null), usd))).hasMessageContaining("moneda");
        assertThatThrownBy(() -> engine.compare(List.of(input("A", "0.09", 24, null),
                input("B", "0.09", 24, new ExtraordinaryPayment(24, new BigDecimal("10.00"), PrepaymentStrategy.REDUCE_TERM)))))
                .hasMessageContaining("antes de la última cuota");
        assertThatThrownBy(() -> engine.compare(List.of(input("A", "0.09", 24, null),
                input("B", "0.09", 24, new ExtraordinaryPayment(2, new BigDecimal("900000.00"), PrepaymentStrategy.REDUCE_TERM)))))
                .hasMessageContaining("saldo");
        var grace = new ScenarioComparisonEngine.ScenarioInput("Gracia", command("0.09", 24, Currency.PEN,
                GracePeriod.of(GracePeriodType.PARTIAL, 6), BalloonPayment.none()), RateTerms.effective(new BigDecimal("0.09")),
                new ExtraordinaryPayment(3, new BigDecimal("100.00"), PrepaymentStrategy.REDUCE_TERM));
        assertThatThrownBy(() -> engine.compare(List.of(input("A", "0.09", 24, null), grace))).hasMessageContaining("gracia");
        var balloon = new ScenarioComparisonEngine.ScenarioInput("Balón", command("0.09", 24, Currency.PEN, GracePeriod.none(),
                BalloonPayment.of(24, new BigDecimal("50000.00"))), RateTerms.effective(new BigDecimal("0.09")),
                new ExtraordinaryPayment(3, new BigDecimal("100.00"), PrepaymentStrategy.REDUCE_TERM));
        assertThatThrownBy(() -> engine.compare(List.of(input("A", "0.09", 24, null), balloon))).hasMessageContaining("balón");
        assertThatThrownBy(() -> new ExtraordinaryPayment(0, BigDecimal.TEN, PrepaymentStrategy.REDUCE_TERM)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new ExtraordinaryPayment(1, new BigDecimal("1.001"), PrepaymentStrategy.REDUCE_TERM)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new ExtraordinaryPayment(1, BigDecimal.ZERO, PrepaymentStrategy.REDUCE_TERM)).isInstanceOf(IllegalArgumentException.class);
    }
}
