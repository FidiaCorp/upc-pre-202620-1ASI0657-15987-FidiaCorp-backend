package com.fidiacorp.credicasa.pricing.domain.service;

import com.fidiacorp.credicasa.pricing.domain.model.BalloonPayment;
import com.fidiacorp.credicasa.pricing.domain.model.ExtraordinaryPayment;
import com.fidiacorp.credicasa.pricing.domain.model.FinancialMetrics;
import com.fidiacorp.credicasa.pricing.domain.model.GracePeriod;
import com.fidiacorp.credicasa.pricing.domain.model.GracePeriodType;
import com.fidiacorp.credicasa.pricing.domain.model.Installment;
import com.fidiacorp.credicasa.pricing.domain.model.PrepaymentStrategy;
import com.fidiacorp.credicasa.pricing.domain.model.Quotation;
import com.fidiacorp.credicasa.pricing.domain.model.RateTerms;
import com.fidiacorp.credicasa.pricing.domain.model.ScenarioComparison;
import com.fidiacorp.credicasa.pricing.domain.model.ScenarioOutcome;
import com.fidiacorp.credicasa.pricing.domain.ports.in.CalculateScheduleCommand;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;

/**
 * US20: compara 2–3 escenarios lado a lado (TCEA, VAN, TIR, cuota y total de intereses) con un prepago opcional por
 * escenario. Dominio puro: reutiliza la {@link AmortizationStrategy} y el {@link FinancialMetricsCalculator}; nada se
 * persiste. El prepago se suma al flujo de la cuota indicada, de modo que TIR/TCEA/VAN reflejan el desembolso real.
 */
public class ScenarioComparisonEngine {
    public record ScenarioInput(String label, CalculateScheduleCommand command, RateTerms rateTerms, ExtraordinaryPayment prepayment) {}

    private final AmortizationStrategy strategy;
    private final FinancialMetricsCalculator metrics;

    public ScenarioComparisonEngine(AmortizationStrategy strategy, FinancialMetricsCalculator metrics) {
        this.strategy = Objects.requireNonNull(strategy, "strategy");
        this.metrics = Objects.requireNonNull(metrics, "metrics");
    }

    public ScenarioComparison compare(List<ScenarioInput> inputs) {
        if (inputs == null || inputs.size() < 2 || inputs.size() > 3) {
            throw new IllegalArgumentException("La comparación requiere entre 2 y 3 escenarios.");
        }
        Set<String> labels = new HashSet<>();
        for (ScenarioInput input : inputs) {
            if (input.label() == null || input.label().isBlank() || !labels.add(input.label().strip())) {
                throw new IllegalArgumentException("Cada escenario requiere una etiqueta única.");
            }
        }
        if (inputs.stream().map(i -> i.command().currency()).distinct().count() > 1) {
            throw new IllegalArgumentException("Los escenarios comparados deben usar la misma moneda.");
        }
        List<ScenarioOutcome> outcomes = inputs.stream().map(this::evaluate).toList();
        return new ScenarioComparison(outcomes,
                best(outcomes, o -> o.metrics().getTcea(), false),
                best(outcomes, o -> o.metrics().getTotalInterest(), false),
                best(outcomes, ScenarioOutcome::monthlyInstallment, false),
                best(outcomes, o -> o.metrics().getVan(), true),
                best(outcomes, o -> o.metrics().getTotalCost(), false));
    }

    private ScenarioOutcome evaluate(ScenarioInput input) {
        CalculateScheduleCommand c = input.command();
        Quotation base = strategy.generateQuotation(c);
        base.assignRateTerms(input.rateTerms());
        BigDecimal monthly = regularInstallment(base.getSchedule());
        if (input.prepayment() == null) {
            return new ScenarioOutcome(input.label().strip(), input.rateTerms(), base, base.getSchedule(), base.getMetrics(), monthly,
                    base.getSchedule().size(), null, BigDecimal.ZERO.setScale(2));
        }
        BigDecimal tem = base.getMetrics().getTem();
        List<Installment> rows = applyPrepayment(base, c, tem, input.prepayment());
        FinancialMetrics adjusted = metrics.calculateMetrics(base.getLoanAmount(), c.annualInterestRate(), tem, rows, c.discountRateAnnual());
        BigDecimal savings = base.getMetrics().getTotalInterest().subtract(adjusted.getTotalInterest()).setScale(2, RoundingMode.HALF_EVEN);
        return new ScenarioOutcome(input.label().strip(), input.rateTerms(), base, rows, adjusted, monthly, rows.size(),
                input.prepayment(), savings);
    }

    List<Installment> applyPrepayment(Quotation base, CalculateScheduleCommand c, BigDecimal tem, ExtraordinaryPayment prepayment) {
        List<Installment> schedule = base.getSchedule();
        int k = prepayment.afterInstallment();
        int graceMonths = base.getGracePeriod().getMonths();
        if (base.getBalloonPayment().isConfigured()) {
            throw new IllegalArgumentException("El prepago no se combina con una cuota balón en el mismo escenario.");
        }
        if (k <= graceMonths || k >= schedule.size()) {
            throw new IllegalArgumentException("El prepago debe aplicarse después del período de gracia y antes de la última cuota.");
        }
        Installment row = schedule.get(k - 1);
        BigDecimal amount = prepayment.amount().setScale(2, RoundingMode.HALF_EVEN);
        if (amount.compareTo(row.getFinalBalance()) > 0) {
            throw new IllegalArgumentException("El prepago no puede superar el saldo pendiente después de la cuota " + k + ".");
        }
        List<Installment> rows = new ArrayList<>(schedule.subList(0, k - 1));
        BigDecimal remaining = row.getFinalBalance().subtract(amount);
        rows.add(new Installment(k, row.getDueDate(), row.getInitialBalance(), row.getInterest(), row.getPrincipalAmortization().add(amount),
                row.getInstallmentAmount().add(amount), row.getLifeInsurance(), row.getPropertyInsurance(), row.getAdminFee(),
                row.getTotalPayment().add(amount), remaining, row.getGraceType()));
        if (remaining.signum() == 0) return rows;
        BigDecimal life = c.lifeInsuranceRate() == null ? BigDecimal.ZERO : c.lifeInsuranceRate();
        BigDecimal insurance = c.propertyInsuranceRate() == null ? BigDecimal.ZERO : c.propertyInsuranceRate();
        BigDecimal fee = c.monthlyAdminFee() == null ? BigDecimal.ZERO : c.monthlyAdminFee();
        int months = prepayment.strategy() == PrepaymentStrategy.REDUCE_INSTALLMENT
                ? schedule.size() - k
                : monthsToKeepInstallment(remaining, tem, schedule.get(graceMonths).getInstallmentAmount(), schedule.size() - k);
        List<Installment> tail = strategy.calculateSchedule(remaining, base.getPropertyValue(), months, tem, row.getDueDate(),
                GracePeriod.none(), BalloonPayment.none(), life, insurance, fee);
        for (Installment t : tail) {
            rows.add(new Installment(k + t.getInstallmentNumber(), t.getDueDate(), t.getInitialBalance(), t.getInterest(),
                    t.getPrincipalAmortization(), t.getInstallmentAmount(), t.getLifeInsurance(), t.getPropertyInsurance(), t.getAdminFee(),
                    t.getTotalPayment(), t.getFinalBalance(), GracePeriodType.NONE));
        }
        return rows;
    }

    /** REDUCE_TERM: menor número de meses cuya cuota nivelada no supera la cuota pura original. */
    private int monthsToKeepInstallment(BigDecimal balance, BigDecimal tem, BigDecimal installment, int maxMonths) {
        for (int m = 1; m < maxMonths; m++) {
            if (strategy.levelInstallment(balance, tem, m).compareTo(installment) <= 0) return m;
        }
        return maxMonths;
    }

    private static BigDecimal regularInstallment(List<Installment> rows) {
        return rows.stream().filter(i -> i.getGraceType() == GracePeriodType.NONE).findFirst()
                .map(Installment::getTotalPayment).orElse(BigDecimal.ZERO);
    }

    private static String best(List<ScenarioOutcome> outcomes, Function<ScenarioOutcome, BigDecimal> metric, boolean highest) {
        Comparator<ScenarioOutcome> comparator = Comparator.comparing(metric);
        return (highest ? outcomes.stream().max(comparator) : outcomes.stream().min(comparator)).orElseThrow().label();
    }
}
