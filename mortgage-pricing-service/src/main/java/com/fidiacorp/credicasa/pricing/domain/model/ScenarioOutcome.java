package com.fidiacorp.credicasa.pricing.domain.model;

import java.math.BigDecimal;
import java.util.List;

/** US20: resultado de un escenario comparado (no se persiste). */
public record ScenarioOutcome(String label, RateTerms rateTerms, Quotation quotation, List<Installment> schedule,
                              FinancialMetrics metrics, BigDecimal monthlyInstallment, int effectiveTermMonths,
                              ExtraordinaryPayment extraordinaryPayment, BigDecimal interestSavings) {
}
