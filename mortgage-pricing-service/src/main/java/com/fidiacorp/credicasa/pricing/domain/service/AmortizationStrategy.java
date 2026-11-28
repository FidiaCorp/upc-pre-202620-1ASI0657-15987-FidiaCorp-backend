package com.fidiacorp.credicasa.pricing.domain.service;

import com.fidiacorp.credicasa.pricing.domain.model.BalloonPayment;
import com.fidiacorp.credicasa.pricing.domain.model.GracePeriod;
import com.fidiacorp.credicasa.pricing.domain.model.Installment;
import com.fidiacorp.credicasa.pricing.domain.model.Quotation;
import com.fidiacorp.credicasa.pricing.domain.ports.in.CalculateScheduleCommand;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * Estrategia de amortización (Figura 6, Cap. IV): el caso de uso depende de esta abstracción y no de un método
 * concreto. {@link FrenchAmortizationEngine} es la implementación vigente (cuota constante, 30/360).
 */
public interface AmortizationStrategy {
    String method();

    Quotation generateQuotation(CalculateScheduleCommand command);

    List<Installment> calculateSchedule(BigDecimal principal, BigDecimal propertyValue, int termMonths, BigDecimal tem,
                                        LocalDate startDate, GracePeriod grace, BalloonPayment balloon, BigDecimal lifeRate,
                                        BigDecimal propertyRate, BigDecimal monthlyFee);

    BigDecimal monthlyRateFromTea(BigDecimal tea);

    BigDecimal levelInstallment(BigDecimal principal, BigDecimal monthlyRate, int months);
}
