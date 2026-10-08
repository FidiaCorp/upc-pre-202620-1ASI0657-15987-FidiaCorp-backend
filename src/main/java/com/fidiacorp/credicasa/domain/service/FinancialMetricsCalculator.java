package com.fidiacorp.credicasa.domain.service;

import com.fidiacorp.credicasa.domain.model.FinancialMetrics;
import com.fidiacorp.credicasa.domain.model.Installment;

import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;
import java.util.List;

public class FinancialMetricsCalculator {

    private static final MathContext MC = MathContext.DECIMAL128;
    private static final int MAX_ITERATIONS = 100;
    private static final double TOLERANCE = 1e-9;

    /**
     * Calculates the Monthly Effective Rate (TEM) from Annual Effective Rate (TEA)
     * using the 30/360 banking convention:
     * TEM = (1 + TEA)^(30/360) - 1 = (1 + TEA)^(1/12) - 1
     */
    public BigDecimal calculateTemFromTea(BigDecimal tea) {
        if (tea == null || tea.compareTo(BigDecimal.ZERO) < 0) {
            throw new IllegalArgumentException("La TEA no puede ser nula ni negativa.");
        }
        double teaDouble = tea.doubleValue();
        double temDouble = Math.pow(1.0 + teaDouble, 30.0 / 360.0) - 1.0;
        return BigDecimal.valueOf(temDouble).setScale(8, RoundingMode.HALF_EVEN);
    }

    /**
     * Calculates the debtor internal rate of return (TIR mensual) and TCEA.
     * Cash flow 0 is the disbursed loan (+loanAmount).
     * Cash flows 1..N are debtor disbursements (-totalPayment).
     */
    public FinancialMetrics calculateMetrics(BigDecimal loanAmount,
                                             BigDecimal tea,
                                             BigDecimal tem,
                                             List<Installment> installments,
                                             BigDecimal discountRateAnnual) {
        if (installments == null || installments.isEmpty()) {
            throw new IllegalArgumentException("El cronograma no puede estar vacío.");
        }

        int n = installments.size();
        double[] cashFlows = new double[n + 1];
        cashFlows[0] = loanAmount.doubleValue();

        BigDecimal sumInterest = BigDecimal.ZERO;
        BigDecimal sumPrincipal = BigDecimal.ZERO;
        BigDecimal sumLifeInsurance = BigDecimal.ZERO;
        BigDecimal sumPropertyInsurance = BigDecimal.ZERO;
        BigDecimal sumAdminFee = BigDecimal.ZERO;
        BigDecimal sumTotalPayment = BigDecimal.ZERO;

        for (int i = 0; i < n; i++) {
            Installment inst = installments.get(i);
            cashFlows[i + 1] = -inst.getTotalPayment().doubleValue();

            sumInterest = sumInterest.add(inst.getInterest());
            sumPrincipal = sumPrincipal.add(inst.getPrincipalAmortization());
            sumLifeInsurance = sumLifeInsurance.add(inst.getLifeInsurance());
            sumPropertyInsurance = sumPropertyInsurance.add(inst.getPropertyInsurance());
            sumAdminFee = sumAdminFee.add(inst.getAdminFee());
            sumTotalPayment = sumTotalPayment.add(inst.getTotalPayment());
        }

        // Calculate TIR using robust Newton-Raphson with Bisection fallback (AC-04)
        double initialGuess = tem.doubleValue();
        double monthlyIrr = solveIrr(cashFlows, initialGuess);
        BigDecimal monthlyTir = BigDecimal.valueOf(monthlyIrr).setScale(8, RoundingMode.HALF_EVEN);

        // Annualized TCEA = (1 + TIR_mensual)^12 - 1
        double tceaDouble = Math.pow(1.0 + monthlyIrr, 12.0) - 1.0;
        BigDecimal tcea = BigDecimal.valueOf(tceaDouble).setScale(6, RoundingMode.HALF_EVEN);

        // VAN calculation using discount rate (COK)
        BigDecimal effectiveDiscountRate = discountRateAnnual != null && discountRateAnnual.compareTo(BigDecimal.ZERO) > 0
                ? discountRateAnnual
                : tea;
        double monthlyDiscountRate = Math.pow(1.0 + effectiveDiscountRate.doubleValue(), 1.0 / 12.0) - 1.0;
        double npv = calculateNpv(cashFlows, monthlyDiscountRate);
        BigDecimal van = BigDecimal.valueOf(npv).setScale(2, RoundingMode.HALF_EVEN);

        return new FinancialMetrics(
                tea.setScale(6, RoundingMode.HALF_EVEN),
                tem.setScale(6, RoundingMode.HALF_EVEN),
                monthlyTir,
                tcea,
                van,
                sumInterest.setScale(2, RoundingMode.HALF_EVEN),
                sumPrincipal.setScale(2, RoundingMode.HALF_EVEN),
                sumLifeInsurance.setScale(2, RoundingMode.HALF_EVEN),
                sumPropertyInsurance.setScale(2, RoundingMode.HALF_EVEN),
                sumAdminFee.setScale(2, RoundingMode.HALF_EVEN),
                sumTotalPayment.setScale(2, RoundingMode.HALF_EVEN),
                effectiveDiscountRate.setScale(6, RoundingMode.HALF_EVEN)
        );
    }

    /**
     * Robust IRR solver: Newton-Raphson bounded solver with Bisection fallback
     * to satisfy AC-04 (TIR/TCEA numerical risk management).
     */
    private double solveIrr(double[] cashFlows, double initialGuess) {
        double rate = initialGuess;

        // Try Newton-Raphson first
        for (int i = 0; i < MAX_ITERATIONS; i++) {
            double fValue = 0.0;
            double fDerivative = 0.0;

            for (int t = 0; t < cashFlows.length; t++) {
                double discountFactor = Math.pow(1.0 + rate, t);
                fValue += cashFlows[t] / discountFactor;
                if (t > 0) {
                    fDerivative -= t * cashFlows[t] / (discountFactor * (1.0 + rate));
                }
            }

            if (Math.abs(fValue) < TOLERANCE) {
                return rate;
            }

            if (Math.abs(fDerivative) < 1e-12) {
                break; // Switch to bisection
            }

            double newRate = rate - (fValue / fDerivative);
            // If new rate explodes or goes negative out of bound, fallback to bisection
            if (newRate <= -0.99 || newRate > 2.0 || Double.isNaN(newRate)) {
                break;
            }

            if (Math.abs(newRate - rate) < TOLERANCE) {
                return newRate;
            }
            rate = newRate;
        }

        // Fallback: Bisection Method on interval [0.000001, 1.0]
        return solveIrrBisection(cashFlows, 0.000001, 1.0);
    }

    private double solveIrrBisection(double[] cashFlows, double low, double high) {
        double fLow = calculateNpv(cashFlows, low);
        double fHigh = calculateNpv(cashFlows, high);

        if (fLow * fHigh > 0) {
            // If signs don't differ on standard interval, expand to -0.5
            low = -0.5;
            fLow = calculateNpv(cashFlows, low);
            if (fLow * fHigh > 0) {
                return Math.max(0.0001, low);
            }
        }

        double mid = low;
        for (int i = 0; i < MAX_ITERATIONS; i++) {
            mid = (low + high) / 2.0;
            double fMid = calculateNpv(cashFlows, mid);

            if (Math.abs(fMid) < TOLERANCE || (high - low) / 2.0 < TOLERANCE) {
                return mid;
            }

            if (fLow * fMid < 0) {
                high = mid;
            } else {
                low = mid;
                fLow = fMid;
            }
        }
        return mid;
    }

    private double calculateNpv(double[] cashFlows, double rate) {
        double npv = 0.0;
        for (int t = 0; t < cashFlows.length; t++) {
            npv += cashFlows[t] / Math.pow(1.0 + rate, t);
        }
        return npv;
    }
}
