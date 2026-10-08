package com.fidiacorp.credicasa.domain.ports.in;

import com.fidiacorp.credicasa.domain.model.BalloonPayment;
import com.fidiacorp.credicasa.domain.model.ClientProfile;
import com.fidiacorp.credicasa.domain.model.Currency;
import com.fidiacorp.credicasa.domain.model.GracePeriod;
import com.fidiacorp.credicasa.domain.model.PropertySnapshot;

import java.math.BigDecimal;
import java.time.LocalDate;

public record CalculateScheduleCommand(
        BigDecimal loanAmount,
        Currency currency,
        int termMonths,
        BigDecimal annualInterestRate,
        GracePeriod gracePeriod,
        BalloonPayment balloonPayment,
        BigDecimal lifeInsuranceRate,
        BigDecimal propertyInsuranceRate,
        BigDecimal propertyValue,
        BigDecimal monthlyAdminFee,
        LocalDate startDate,
        ClientProfile clientProfile,
        PropertySnapshot propertySnapshot,
        BigDecimal discountRateAnnual
) {
}
