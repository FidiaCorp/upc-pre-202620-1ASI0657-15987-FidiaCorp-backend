package com.fidiacorp.credicasa.domain.model;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public class BankRateBenchmark {

    private final String bankCode;
    private final String bankName;
    private final BigDecimal minTea;
    private final BigDecimal maxTea;
    private final BigDecimal averageTea;
    private final Currency currency;
    private final String productName;
    private final Integer maxTermMonths;
    private final BigDecimal maxLtv;
    private final LocalDateTime updatedAt;

    public BankRateBenchmark(String bankCode,
                             String bankName,
                             BigDecimal minTea,
                             BigDecimal maxTea,
                             BigDecimal averageTea,
                             Currency currency,
                             String productName,
                             Integer maxTermMonths,
                             BigDecimal maxLtv,
                             LocalDateTime updatedAt) {
        this.bankCode = bankCode;
        this.bankName = bankName;
        this.minTea = minTea;
        this.maxTea = maxTea;
        this.averageTea = averageTea;
        this.currency = currency;
        this.productName = productName;
        this.maxTermMonths = maxTermMonths;
        this.maxLtv = maxLtv;
        this.updatedAt = updatedAt != null ? updatedAt : LocalDateTime.now();
    }

    public String getBankCode() {
        return bankCode;
    }

    public String getBankName() {
        return bankName;
    }

    public BigDecimal getMinTea() {
        return minTea;
    }

    public BigDecimal getMaxTea() {
        return maxTea;
    }

    public BigDecimal getAverageTea() {
        return averageTea;
    }

    public Currency getCurrency() {
        return currency;
    }

    public String getProductName() {
        return productName;
    }

    public Integer getMaxTermMonths() {
        return maxTermMonths;
    }

    public BigDecimal getMaxLtv() {
        return maxLtv;
    }

    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }
}
