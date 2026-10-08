package com.fidiacorp.credicasa.domain.model;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

public class Quotation {

    private final UUID id;
    private final BigDecimal loanAmount;
    private final Currency currency;
    private final int termMonths;
    private final BigDecimal annualInterestRate;
    private final GracePeriod gracePeriod;
    private final BalloonPayment balloonPayment;
    private final BigDecimal lifeInsuranceRate;
    private final BigDecimal propertyInsuranceRate;
    private final BigDecimal propertyValue;
    private final BigDecimal monthlyAdminFee;
    private final LocalDate startDate;
    private final ClientProfile clientProfile;
    private final PropertySnapshot propertySnapshot;
    private final List<Installment> schedule;
    private final FinancialMetrics metrics;
    private QuotationStatus status;
    private final LocalDateTime createdAt;

    public Quotation(UUID id,
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
                     List<Installment> schedule,
                     FinancialMetrics metrics,
                     QuotationStatus status,
                     LocalDateTime createdAt) {
        this.id = id != null ? id : UUID.randomUUID();
        this.loanAmount = loanAmount;
        this.currency = currency != null ? currency : Currency.PEN;
        this.termMonths = termMonths;
        this.annualInterestRate = annualInterestRate;
        this.gracePeriod = gracePeriod != null ? gracePeriod : GracePeriod.none();
        this.balloonPayment = balloonPayment != null ? balloonPayment : BalloonPayment.none();
        this.lifeInsuranceRate = lifeInsuranceRate != null ? lifeInsuranceRate : BigDecimal.ZERO;
        this.propertyInsuranceRate = propertyInsuranceRate != null ? propertyInsuranceRate : BigDecimal.ZERO;
        this.propertyValue = propertyValue != null ? propertyValue : BigDecimal.ZERO;
        this.monthlyAdminFee = monthlyAdminFee != null ? monthlyAdminFee : BigDecimal.ZERO;
        this.startDate = startDate != null ? startDate : LocalDate.now();
        this.clientProfile = clientProfile;
        this.propertySnapshot = propertySnapshot;
        this.schedule = schedule != null ? new ArrayList<>(schedule) : new ArrayList<>();
        this.metrics = metrics;
        this.status = status != null ? status : QuotationStatus.ISSUED;
        this.createdAt = createdAt != null ? createdAt : LocalDateTime.now();
    }

    public UUID getId() {
        return id;
    }

    public BigDecimal getLoanAmount() {
        return loanAmount;
    }

    public Currency getCurrency() {
        return currency;
    }

    public int getTermMonths() {
        return termMonths;
    }

    public BigDecimal getAnnualInterestRate() {
        return annualInterestRate;
    }

    public GracePeriod getGracePeriod() {
        return gracePeriod;
    }

    public BalloonPayment getBalloonPayment() {
        return balloonPayment;
    }

    public BigDecimal getLifeInsuranceRate() {
        return lifeInsuranceRate;
    }

    public BigDecimal getPropertyInsuranceRate() {
        return propertyInsuranceRate;
    }

    public BigDecimal getPropertyValue() {
        return propertyValue;
    }

    public BigDecimal getMonthlyAdminFee() {
        return monthlyAdminFee;
    }

    public LocalDate getStartDate() {
        return startDate;
    }

    public ClientProfile getClientProfile() {
        return clientProfile;
    }

    public PropertySnapshot getPropertySnapshot() {
        return propertySnapshot;
    }

    public List<Installment> getSchedule() {
        return Collections.unmodifiableList(schedule);
    }

    public FinancialMetrics getMetrics() {
        return metrics;
    }

    public QuotationStatus getStatus() {
        return status;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void markAsAccepted() {
        this.status = QuotationStatus.ACCEPTED;
    }

    public void markAsExpired() {
        this.status = QuotationStatus.EXPIRED;
    }
}
