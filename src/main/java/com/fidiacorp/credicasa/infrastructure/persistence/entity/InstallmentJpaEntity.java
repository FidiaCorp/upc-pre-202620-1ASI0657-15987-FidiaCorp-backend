package com.fidiacorp.credicasa.infrastructure.persistence.entity;

import com.fidiacorp.credicasa.domain.model.GracePeriodType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.time.LocalDate;

@Entity
@Table(name = "installments")
public class InstallmentJpaEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "quotation_id", nullable = false)
    private QuotationJpaEntity quotation;

    @Column(name = "installment_number", nullable = false)
    private int installmentNumber;

    @Column(name = "due_date", nullable = false)
    private LocalDate dueDate;

    @Column(name = "initial_balance", precision = 38, scale = 2, nullable = false)
    private BigDecimal initialBalance;

    @Column(name = "interest", precision = 38, scale = 2, nullable = false)
    private BigDecimal interest;

    @Column(name = "principal_amortization", precision = 38, scale = 2, nullable = false)
    private BigDecimal principalAmortization;

    @Column(name = "installment_amount", precision = 38, scale = 2, nullable = false)
    private BigDecimal installmentAmount;

    @Column(name = "life_insurance", precision = 38, scale = 2, nullable = false)
    private BigDecimal lifeInsurance;

    @Column(name = "property_insurance", precision = 38, scale = 2, nullable = false)
    private BigDecimal propertyInsurance;

    @Column(name = "admin_fee", precision = 38, scale = 2, nullable = false)
    private BigDecimal adminFee;

    @Column(name = "total_payment", precision = 38, scale = 2, nullable = false)
    private BigDecimal totalPayment;

    @Column(name = "final_balance", precision = 38, scale = 2, nullable = false)
    private BigDecimal finalBalance;

    @Enumerated(EnumType.STRING)
    @Column(name = "grace_type", length = 20, nullable = false)
    private GracePeriodType graceType;

    public InstallmentJpaEntity() {}

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public QuotationJpaEntity getQuotation() { return quotation; }
    public void setQuotation(QuotationJpaEntity quotation) { this.quotation = quotation; }

    public int getInstallmentNumber() { return installmentNumber; }
    public void setInstallmentNumber(int installmentNumber) { this.installmentNumber = installmentNumber; }

    public LocalDate getDueDate() { return dueDate; }
    public void setDueDate(LocalDate dueDate) { this.dueDate = dueDate; }

    public BigDecimal getInitialBalance() { return initialBalance; }
    public void setInitialBalance(BigDecimal initialBalance) { this.initialBalance = initialBalance; }

    public BigDecimal getInterest() { return interest; }
    public void setInterest(BigDecimal interest) { this.interest = interest; }

    public BigDecimal getPrincipalAmortization() { return principalAmortization; }
    public void setPrincipalAmortization(BigDecimal principalAmortization) { this.principalAmortization = principalAmortization; }

    public BigDecimal getInstallmentAmount() { return installmentAmount; }
    public void setInstallmentAmount(BigDecimal installmentAmount) { this.installmentAmount = installmentAmount; }

    public BigDecimal getLifeInsurance() { return lifeInsurance; }
    public void setLifeInsurance(BigDecimal lifeInsurance) { this.lifeInsurance = lifeInsurance; }

    public BigDecimal getPropertyInsurance() { return propertyInsurance; }
    public void setPropertyInsurance(BigDecimal propertyInsurance) { this.propertyInsurance = propertyInsurance; }

    public BigDecimal getAdminFee() { return adminFee; }
    public void setAdminFee(BigDecimal adminFee) { this.adminFee = adminFee; }

    public BigDecimal getTotalPayment() { return totalPayment; }
    public void setTotalPayment(BigDecimal totalPayment) { this.totalPayment = totalPayment; }

    public BigDecimal getFinalBalance() { return finalBalance; }
    public void setFinalBalance(BigDecimal finalBalance) { this.finalBalance = finalBalance; }

    public GracePeriodType getGraceType() { return graceType; }
    public void setGraceType(GracePeriodType graceType) { this.graceType = graceType; }
}
