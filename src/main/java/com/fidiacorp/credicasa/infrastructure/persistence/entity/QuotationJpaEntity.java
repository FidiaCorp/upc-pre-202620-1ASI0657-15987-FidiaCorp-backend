package com.fidiacorp.credicasa.infrastructure.persistence.entity;

import com.fidiacorp.credicasa.domain.model.Currency;
import com.fidiacorp.credicasa.domain.model.GracePeriodType;
import com.fidiacorp.credicasa.domain.model.QuotationStatus;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "quotations")
public class QuotationJpaEntity {

    @Id
    private UUID id;

    // Nullable only for historical rows: unowned rows are never exposed through REST.
    @Column(name="owner_id") private UUID ownerId;
    @Column(name="property_id") private UUID propertyId;
    @Column(name="client_id") private UUID clientId;
    @Column(name="discount_rate_used",precision=38,scale=24) private BigDecimal discountRateUsed;

    @Column(name = "loan_amount", precision = 38, scale = 2, nullable = false)
    private BigDecimal loanAmount;

    @Enumerated(EnumType.STRING)
    @Column(name = "currency", length = 10, nullable = false)
    private Currency currency;

    @Column(name = "term_months", nullable = false)
    private int termMonths;

    @Column(name = "annual_interest_rate", precision = 38, scale = 24, nullable = false)
    private BigDecimal annualInterestRate;

    @Enumerated(EnumType.STRING)
    @Column(name = "grace_period_type", length = 20, nullable = false)
    private GracePeriodType gracePeriodType;

    @Column(name = "grace_period_months", nullable = false)
    private int gracePeriodMonths;

    @Column(name = "balloon_amount", precision = 38, scale = 2)
    private BigDecimal balloonAmount;

    @Column(name = "balloon_month")
    private Integer balloonMonth;

    @Column(name = "life_insurance_rate", precision = 38, scale = 24)
    private BigDecimal lifeInsuranceRate;

    @Column(name = "property_insurance_rate", precision = 38, scale = 24)
    private BigDecimal propertyInsuranceRate;

    @Column(name = "property_value", precision = 38, scale = 2)
    private BigDecimal propertyValue;

    @Column(name = "monthly_admin_fee", precision = 38, scale = 2)
    private BigDecimal monthlyAdminFee;

    @Column(name = "start_date", nullable = false)
    private LocalDate startDate;

    // Financial Metrics
    @Column(name = "tea", precision = 38, scale = 24, nullable = false)
    private BigDecimal tea;

    @Column(name = "tem", precision = 38, scale = 24, nullable = false)
    private BigDecimal tem;

    @Column(name = "monthly_tir", precision = 60, scale = 24, nullable = false)
    private BigDecimal monthlyTir;

    @Column(name = "tcea", precision = 280, scale = 24, nullable = false)
    private BigDecimal tcea;

    @Column(name = "van", precision = 38, scale = 2, nullable = false)
    private BigDecimal van;

    @Column(name = "total_interest", precision = 38, scale = 2, nullable = false)
    private BigDecimal totalInterest;

    @Column(name = "total_cost", precision = 38, scale = 2, nullable = false)
    private BigDecimal totalCost;

    // Client Snapshot
    @Column(name = "client_doc", length = 30)
    private String clientDoc;

    @Column(name = "client_name", length = 201)
    private String clientName;

    @Column(name = "client_email", length = 254)
    private String clientEmail;

    // Property Snapshot
    @Column(name = "property_address", length = 250)
    private String propertyAddress;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", length = 20, nullable = false)
    private QuotationStatus status;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @OrderBy("installmentNumber ASC")
    @OneToMany(mappedBy = "quotation", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<InstallmentJpaEntity> installments = new ArrayList<>();

    public QuotationJpaEntity() {}

    public void addInstallment(InstallmentJpaEntity installment) {
        installments.add(installment);
        installment.setQuotation(this);
    }

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }

    public UUID getOwnerId() { return ownerId; }
    public void setOwnerId(UUID ownerId) { this.ownerId = ownerId; }

    public UUID getPropertyId() { return propertyId; }
    public void setPropertyId(UUID propertyId) { this.propertyId = propertyId; }

    public UUID getClientId() { return clientId; }
    public void setClientId(UUID clientId) { this.clientId = clientId; }

    public BigDecimal getDiscountRateUsed() { return discountRateUsed; }
    public void setDiscountRateUsed(BigDecimal discountRateUsed) { this.discountRateUsed = discountRateUsed; }

    public BigDecimal getLoanAmount() { return loanAmount; }
    public void setLoanAmount(BigDecimal loanAmount) { this.loanAmount = loanAmount; }

    public Currency getCurrency() { return currency; }
    public void setCurrency(Currency currency) { this.currency = currency; }

    public int getTermMonths() { return termMonths; }
    public void setTermMonths(int termMonths) { this.termMonths = termMonths; }

    public BigDecimal getAnnualInterestRate() { return annualInterestRate; }
    public void setAnnualInterestRate(BigDecimal annualInterestRate) { this.annualInterestRate = annualInterestRate; }

    public GracePeriodType getGracePeriodType() { return gracePeriodType; }
    public void setGracePeriodType(GracePeriodType gracePeriodType) { this.gracePeriodType = gracePeriodType; }

    public int getGracePeriodMonths() { return gracePeriodMonths; }
    public void setGracePeriodMonths(int gracePeriodMonths) { this.gracePeriodMonths = gracePeriodMonths; }

    public BigDecimal getBalloonAmount() { return balloonAmount; }
    public void setBalloonAmount(BigDecimal balloonAmount) { this.balloonAmount = balloonAmount; }

    public Integer getBalloonMonth() { return balloonMonth; }
    public void setBalloonMonth(Integer balloonMonth) { this.balloonMonth = balloonMonth; }

    public BigDecimal getLifeInsuranceRate() { return lifeInsuranceRate; }
    public void setLifeInsuranceRate(BigDecimal lifeInsuranceRate) { this.lifeInsuranceRate = lifeInsuranceRate; }

    public BigDecimal getPropertyInsuranceRate() { return propertyInsuranceRate; }
    public void setPropertyInsuranceRate(BigDecimal propertyInsuranceRate) { this.propertyInsuranceRate = propertyInsuranceRate; }

    public BigDecimal getPropertyValue() { return propertyValue; }
    public void setPropertyValue(BigDecimal propertyValue) { this.propertyValue = propertyValue; }

    public BigDecimal getMonthlyAdminFee() { return monthlyAdminFee; }
    public void setMonthlyAdminFee(BigDecimal monthlyAdminFee) { this.monthlyAdminFee = monthlyAdminFee; }

    public LocalDate getStartDate() { return startDate; }
    public void setStartDate(LocalDate startDate) { this.startDate = startDate; }

    public BigDecimal getTea() { return tea; }
    public void setTea(BigDecimal tea) { this.tea = tea; }

    public BigDecimal getTem() { return tem; }
    public void setTem(BigDecimal tem) { this.tem = tem; }

    public BigDecimal getMonthlyTir() { return monthlyTir; }
    public void setMonthlyTir(BigDecimal monthlyTir) { this.monthlyTir = monthlyTir; }

    public BigDecimal getTcea() { return tcea; }
    public void setTcea(BigDecimal tcea) { this.tcea = tcea; }

    public BigDecimal getVan() { return van; }
    public void setVan(BigDecimal van) { this.van = van; }

    public BigDecimal getTotalInterest() { return totalInterest; }
    public void setTotalInterest(BigDecimal totalInterest) { this.totalInterest = totalInterest; }

    public BigDecimal getTotalCost() { return totalCost; }
    public void setTotalCost(BigDecimal totalCost) { this.totalCost = totalCost; }

    public String getClientDoc() { return clientDoc; }
    public void setClientDoc(String clientDoc) { this.clientDoc = clientDoc; }

    public String getClientName() { return clientName; }
    public void setClientName(String clientName) { this.clientName = clientName; }

    public String getClientEmail() { return clientEmail; }
    public void setClientEmail(String clientEmail) { this.clientEmail = clientEmail; }

    public String getPropertyAddress() { return propertyAddress; }
    public void setPropertyAddress(String propertyAddress) { this.propertyAddress = propertyAddress; }

    public QuotationStatus getStatus() { return status; }
    public void setStatus(QuotationStatus status) { this.status = status; }

    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }

    public List<InstallmentJpaEntity> getInstallments() { return installments; }
    public void setInstallments(List<InstallmentJpaEntity> installments) { this.installments = installments; }
}
