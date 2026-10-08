package com.fidiacorp.credicasa.domain.service;

import com.fidiacorp.credicasa.domain.model.BalloonPayment;
import com.fidiacorp.credicasa.domain.model.Currency;
import com.fidiacorp.credicasa.domain.model.FinancialMetrics;
import com.fidiacorp.credicasa.domain.model.GracePeriod;
import com.fidiacorp.credicasa.domain.model.GracePeriodType;
import com.fidiacorp.credicasa.domain.model.Installment;
import com.fidiacorp.credicasa.domain.model.Quotation;
import com.fidiacorp.credicasa.domain.model.QuotationStatus;
import com.fidiacorp.credicasa.domain.ports.in.CalculateScheduleCommand;

import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Driver 2: Rendimiento y Precisión (ASR-PERF)
 * Motor Financiero Determinado bajo el Método Francés Ordinario Vencido (Convención 30/360).
 * Garantiza precisión con BigDecimal (escala 6 para tasas, HALF_EVEN a 2 decimales para importes)
 * y liquidación del saldo insoluto final a 0.00 exacto (AC-02).
 */
public class FrenchAmortizationEngine {

    private static final RoundingMode CURRENCY_ROUNDING = RoundingMode.HALF_EVEN;
    private static final int CURRENCY_SCALE = 2;
    private static final int RATE_SCALE = 6;
    private static final MathContext MC_PRECISE = MathContext.DECIMAL128;

    private final FinancialMetricsCalculator metricsCalculator;

    public FrenchAmortizationEngine() {
        this.metricsCalculator = new FinancialMetricsCalculator();
    }

    public FrenchAmortizationEngine(FinancialMetricsCalculator metricsCalculator) {
        this.metricsCalculator = metricsCalculator != null ? metricsCalculator : new FinancialMetricsCalculator();
    }

    /**
     * Generates the complete quotation and amortization schedule according to the French method.
     */
    public Quotation generateQuotation(CalculateScheduleCommand command) {
        validateCommand(command);

        BigDecimal loanAmount = command.loanAmount().setScale(CURRENCY_SCALE, CURRENCY_ROUNDING);
        BigDecimal propertyValue = command.propertyValue() != null ? command.propertyValue() : loanAmount;
        int termMonths = command.termMonths();
        BigDecimal tea = command.annualInterestRate();
        BigDecimal tem = metricsCalculator.calculateTemFromTea(tea);
        LocalDate startDate = command.startDate() != null ? command.startDate() : LocalDate.now();

        GracePeriod gracePeriod = command.gracePeriod() != null ? command.gracePeriod() : GracePeriod.none();
        BalloonPayment balloonPayment = command.balloonPayment() != null ? command.balloonPayment() : BalloonPayment.none();
        BigDecimal lifeInsuranceRate = command.lifeInsuranceRate() != null ? command.lifeInsuranceRate() : BigDecimal.ZERO;
        BigDecimal propertyInsuranceRate = command.propertyInsuranceRate() != null ? command.propertyInsuranceRate() : BigDecimal.ZERO;
        BigDecimal monthlyAdminFee = command.monthlyAdminFee() != null ? command.monthlyAdminFee() : BigDecimal.ZERO;

        List<Installment> schedule = calculateSchedule(
                loanAmount,
                propertyValue,
                termMonths,
                tem,
                startDate,
                gracePeriod,
                balloonPayment,
                lifeInsuranceRate,
                propertyInsuranceRate,
                monthlyAdminFee
        );

        FinancialMetrics metrics = metricsCalculator.calculateMetrics(
                loanAmount,
                tea,
                tem,
                schedule,
                command.discountRateAnnual()
        );

        return new Quotation(
                UUID.randomUUID(),
                loanAmount,
                command.currency() != null ? command.currency() : Currency.PEN,
                termMonths,
                tea.setScale(RATE_SCALE, CURRENCY_ROUNDING),
                gracePeriod,
                balloonPayment,
                lifeInsuranceRate,
                propertyInsuranceRate,
                propertyValue,
                monthlyAdminFee,
                startDate,
                command.clientProfile(),
                command.propertySnapshot(),
                schedule,
                metrics,
                QuotationStatus.ISSUED,
                LocalDateTime.now()
        );
    }

    /**
     * Calculates the step-by-step amortization schedule.
     */
    public List<Installment> calculateSchedule(BigDecimal principal,
                                               BigDecimal propertyValue,
                                               int termMonths,
                                               BigDecimal tem,
                                               LocalDate startDate,
                                               GracePeriod gracePeriod,
                                               BalloonPayment balloonPayment,
                                               BigDecimal lifeInsuranceRate,
                                               BigDecimal propertyInsuranceRate,
                                               BigDecimal monthlyAdminFee) {
        List<Installment> schedule = new ArrayList<>(termMonths);
        BigDecimal currentBalance = principal;
        double temDouble = tem.doubleValue();
        int graceMonths = gracePeriod.getMonths();
        GracePeriodType graceType = gracePeriod.getType();

        // 1. If Grace Period is configured, simulate grace months first
        for (int k = 1; k <= graceMonths && k <= termMonths; k++) {
            LocalDate dueDate = startDate.plusMonths(k);
            BigDecimal initialBalance = currentBalance;
            BigDecimal interest = initialBalance.multiply(BigDecimal.valueOf(temDouble), MC_PRECISE)
                    .setScale(CURRENCY_SCALE, CURRENCY_ROUNDING);

            BigDecimal lifeInsurance = initialBalance.multiply(lifeInsuranceRate, MC_PRECISE)
                    .setScale(CURRENCY_SCALE, CURRENCY_ROUNDING);
            BigDecimal propertyInsurance = propertyValue.multiply(propertyInsuranceRate, MC_PRECISE)
                    .setScale(CURRENCY_SCALE, CURRENCY_ROUNDING);
            BigDecimal adminFee = monthlyAdminFee.setScale(CURRENCY_SCALE, CURRENCY_ROUNDING);

            BigDecimal amortization;
            BigDecimal installmentAmount;
            BigDecimal finalBalance;

            if (graceType == GracePeriodType.TOTAL) {
                // Total Grace: No principal or interest payment. Interest is capitalized to principal.
                amortization = BigDecimal.ZERO.setScale(CURRENCY_SCALE, CURRENCY_ROUNDING);
                installmentAmount = BigDecimal.ZERO.setScale(CURRENCY_SCALE, CURRENCY_ROUNDING);
                finalBalance = initialBalance.add(interest);
                currentBalance = finalBalance;
            } else {
                // Partial Grace: Interest is paid, amortization is zero. Balance remains constant.
                amortization = BigDecimal.ZERO.setScale(CURRENCY_SCALE, CURRENCY_ROUNDING);
                installmentAmount = interest;
                finalBalance = initialBalance;
                currentBalance = finalBalance;
            }

            BigDecimal totalPayment = installmentAmount.add(lifeInsurance).add(propertyInsurance).add(adminFee);

            schedule.add(new Installment(
                    k,
                    dueDate,
                    initialBalance,
                    interest,
                    amortization,
                    installmentAmount,
                    lifeInsurance,
                    propertyInsurance,
                    adminFee,
                    totalPayment,
                    finalBalance,
                    graceType
            ));
        }

        // 2. Regular amortization phase for remaining months
        int regularMonths = termMonths - graceMonths;
        if (regularMonths > 0) {
            BigDecimal regularInstallment = calculateFrenchRegularInstallment(
                    currentBalance,
                    temDouble,
                    regularMonths,
                    balloonPayment
            );

            for (int m = 1; m <= regularMonths; m++) {
                int installmentNumber = graceMonths + m;
                LocalDate dueDate = startDate.plusMonths(installmentNumber);
                BigDecimal initialBalance = currentBalance;

                BigDecimal interest = initialBalance.multiply(BigDecimal.valueOf(temDouble), MC_PRECISE)
                        .setScale(CURRENCY_SCALE, CURRENCY_ROUNDING);

                BigDecimal lifeInsurance = initialBalance.multiply(lifeInsuranceRate, MC_PRECISE)
                        .setScale(CURRENCY_SCALE, CURRENCY_ROUNDING);
                BigDecimal propertyInsurance = propertyValue.multiply(propertyInsuranceRate, MC_PRECISE)
                        .setScale(CURRENCY_SCALE, CURRENCY_ROUNDING);
                BigDecimal adminFee = monthlyAdminFee.setScale(CURRENCY_SCALE, CURRENCY_ROUNDING);

                BigDecimal amortization;
                BigDecimal installmentAmount;
                BigDecimal finalBalance;

                if (m == regularMonths) {
                    // AC-02: Liquidación de última cuota garantizando saldo insoluto a 0.00 exacto
                    amortization = initialBalance;
                    installmentAmount = amortization.add(interest);
                    finalBalance = BigDecimal.ZERO.setScale(CURRENCY_SCALE, CURRENCY_ROUNDING);
                    currentBalance = finalBalance;
                } else {
                    amortization = regularInstallment.subtract(interest);
                    // Defensive check: if amortization exceeds balance
                    if (amortization.compareTo(initialBalance) > 0) {
                        amortization = initialBalance;
                    }
                    installmentAmount = regularInstallment;
                    finalBalance = initialBalance.subtract(amortization).setScale(CURRENCY_SCALE, CURRENCY_ROUNDING);
                    currentBalance = finalBalance;
                }

                BigDecimal totalPayment = installmentAmount.add(lifeInsurance).add(propertyInsurance).add(adminFee);

                schedule.add(new Installment(
                        installmentNumber,
                        dueDate,
                        initialBalance,
                        interest,
                        amortization,
                        installmentAmount,
                        lifeInsurance,
                        propertyInsurance,
                        adminFee,
                        totalPayment,
                        finalBalance,
                        GracePeriodType.NONE
                ));
            }
        }

        return schedule;
    }

    /**
     * Calculates the constant French installment R.
     * Incorporates balloon payment if configured:
     * R = [P - B * (1 + i)^(-m)] / [ (1 - (1 + i)^(-m)) / i ]
     */
    private BigDecimal calculateFrenchRegularInstallment(BigDecimal principal,
                                                         double tem,
                                                         int months,
                                                         BalloonPayment balloon) {
        if (months <= 0) {
            return BigDecimal.ZERO;
        }

        double p = principal.doubleValue();
        double factor = Math.pow(1.0 + tem, -months);
        double annuityPresentValueFactor = (1.0 - factor) / tem;

        double r;
        if (balloon != null && balloon.isConfigured()) {
            double b = balloon.getAmount().doubleValue();
            double balloonPv = b * factor;
            r = (p - balloonPv) / annuityPresentValueFactor;
        } else {
            r = p / annuityPresentValueFactor;
        }

        return BigDecimal.valueOf(r).setScale(CURRENCY_SCALE, CURRENCY_ROUNDING);
    }

    private void validateCommand(CalculateScheduleCommand command) {
        if (command == null) {
            throw new IllegalArgumentException("El comando de simulación no puede ser nulo.");
        }
        if (command.loanAmount() == null || command.loanAmount().compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("El monto del préstamo debe ser mayor a cero.");
        }
        if (command.termMonths() <= 0 || command.termMonths() > 360) {
            throw new IllegalArgumentException("El plazo en meses debe estar entre 1 y 360.");
        }
        if (command.annualInterestRate() == null || command.annualInterestRate().compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("La tasa de interés anual (TEA) debe ser mayor a cero.");
        }
        if (command.gracePeriod() != null && command.gracePeriod().getMonths() >= command.termMonths()) {
            throw new IllegalArgumentException("El período de gracia no puede ser mayor o igual al plazo total.");
        }
    }
}
