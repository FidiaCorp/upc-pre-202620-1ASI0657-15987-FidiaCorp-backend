package com.fidiacorp.credicasa.domain.model;

import java.math.BigDecimal;
import java.time.LocalDate;

public class Installment {

    private final int installmentNumber;
    private final LocalDate dueDate;
    private final BigDecimal initialBalance;
    private final BigDecimal interest;
    private final BigDecimal principalAmortization;
    private final BigDecimal installmentAmount;
    private final BigDecimal lifeInsurance;
    private final BigDecimal propertyInsurance;
    private final BigDecimal adminFee;
    private final BigDecimal totalPayment;
    private final BigDecimal finalBalance;
    private final GracePeriodType graceType;

    public Installment(int installmentNumber,
                       LocalDate dueDate,
                       BigDecimal initialBalance,
                       BigDecimal interest,
                       BigDecimal principalAmortization,
                       BigDecimal installmentAmount,
                       BigDecimal lifeInsurance,
                       BigDecimal propertyInsurance,
                       BigDecimal adminFee,
                       BigDecimal totalPayment,
                       BigDecimal finalBalance,
                       GracePeriodType graceType) {
        this.installmentNumber = installmentNumber;
        this.dueDate = dueDate;
        this.initialBalance = initialBalance;
        this.interest = interest;
        this.principalAmortization = principalAmortization;
        this.installmentAmount = installmentAmount;
        this.lifeInsurance = lifeInsurance;
        this.propertyInsurance = propertyInsurance;
        this.adminFee = adminFee;
        this.totalPayment = totalPayment;
        this.finalBalance = finalBalance;
        this.graceType = graceType != null ? graceType : GracePeriodType.NONE;
    }

    public int getInstallmentNumber() {
        return installmentNumber;
    }

    public LocalDate getDueDate() {
        return dueDate;
    }

    public BigDecimal getInitialBalance() {
        return initialBalance;
    }

    public BigDecimal getInterest() {
        return interest;
    }

    public BigDecimal getPrincipalAmortization() {
        return principalAmortization;
    }

    public BigDecimal getInstallmentAmount() {
        return installmentAmount;
    }

    public BigDecimal getLifeInsurance() {
        return lifeInsurance;
    }

    public BigDecimal getPropertyInsurance() {
        return propertyInsurance;
    }

    public BigDecimal getAdminFee() {
        return adminFee;
    }

    public BigDecimal getTotalPayment() {
        return totalPayment;
    }

    public BigDecimal getFinalBalance() {
        return finalBalance;
    }

    public GracePeriodType getGraceType() {
        return graceType;
    }
}
