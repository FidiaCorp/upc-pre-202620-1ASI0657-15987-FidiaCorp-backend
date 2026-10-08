package com.fidiacorp.credicasa.domain.model;

import java.math.BigDecimal;

public class FinancialMetrics {

    private final BigDecimal tea;
    private final BigDecimal tem;
    private final BigDecimal monthlyTir;
    private final BigDecimal tcea;
    private final BigDecimal van;
    private final BigDecimal totalInterest;
    private final BigDecimal totalPrincipal;
    private final BigDecimal totalLifeInsurance;
    private final BigDecimal totalPropertyInsurance;
    private final BigDecimal totalAdminFee;
    private final BigDecimal totalCost;
    private final BigDecimal discountRateUsed;

    public FinancialMetrics(BigDecimal tea,
                            BigDecimal tem,
                            BigDecimal monthlyTir,
                            BigDecimal tcea,
                            BigDecimal van,
                            BigDecimal totalInterest,
                            BigDecimal totalPrincipal,
                            BigDecimal totalLifeInsurance,
                            BigDecimal totalPropertyInsurance,
                            BigDecimal totalAdminFee,
                            BigDecimal totalCost,
                            BigDecimal discountRateUsed) {
        this.tea = tea;
        this.tem = tem;
        this.monthlyTir = monthlyTir;
        this.tcea = tcea;
        this.van = van;
        this.totalInterest = totalInterest;
        this.totalPrincipal = totalPrincipal;
        this.totalLifeInsurance = totalLifeInsurance;
        this.totalPropertyInsurance = totalPropertyInsurance;
        this.totalAdminFee = totalAdminFee;
        this.totalCost = totalCost;
        this.discountRateUsed = discountRateUsed;
    }

    public BigDecimal getTea() {
        return tea;
    }

    public BigDecimal getTem() {
        return tem;
    }

    public BigDecimal getMonthlyTir() {
        return monthlyTir;
    }

    public BigDecimal getTcea() {
        return tcea;
    }

    public BigDecimal getVan() {
        return van;
    }

    public BigDecimal getTotalInterest() {
        return totalInterest;
    }

    public BigDecimal getTotalPrincipal() {
        return totalPrincipal;
    }

    public BigDecimal getTotalLifeInsurance() {
        return totalLifeInsurance;
    }

    public BigDecimal getTotalPropertyInsurance() {
        return totalPropertyInsurance;
    }

    public BigDecimal getTotalAdminFee() {
        return totalAdminFee;
    }

    public BigDecimal getTotalCost() {
        return totalCost;
    }

    public BigDecimal getDiscountRateUsed() {
        return discountRateUsed;
    }
}
