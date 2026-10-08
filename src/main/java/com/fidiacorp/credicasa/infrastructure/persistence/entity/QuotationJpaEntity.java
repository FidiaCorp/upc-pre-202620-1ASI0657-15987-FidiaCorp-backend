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
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "quotations")
@Getter
@Setter
@NoArgsConstructor
public class QuotationJpaEntity {

    @Id
    private UUID id;

    @Column(name = "loan_amount", precision = 18, scale = 2, nullable = false)
    private BigDecimal loanAmount;

    @Enumerated(EnumType.STRING)
    @Column(name = "currency", length = 10, nullable = false)
    private Currency currency;

    @Column(name = "term_months", nullable = false)
    private int termMonths;

    @Column(name = "annual_interest_rate", precision = 10, scale = 6, nullable = false)
    private BigDecimal annualInterestRate;

    @Enumerated(EnumType.STRING)
    @Column(name = "grace_period_type", length = 20, nullable = false)
    private GracePeriodType gracePeriodType;

    @Column(name = "grace_period_months", nullable = false)
    private int gracePeriodMonths;

    @Column(name = "balloon_amount", precision = 18, scale = 2)
    private BigDecimal balloonAmount;

    @Column(name = "balloon_month")
    private Integer balloonMonth;

    @Column(name = "life_insurance_rate", precision = 10, scale = 6)
    private BigDecimal lifeInsuranceRate;

    @Column(name = "property_insurance_rate", precision = 10, scale = 6)
    private BigDecimal propertyInsuranceRate;

    @Column(name = "property_value", precision = 18, scale = 2)
    private BigDecimal propertyValue;

    @Column(name = "monthly_admin_fee", precision = 18, scale = 2)
    private BigDecimal monthlyAdminFee;

    @Column(name = "start_date", nullable = false)
    private LocalDate startDate;

    // Financial Metrics
    @Column(name = "tea", precision = 10, scale = 6, nullable = false)
    private BigDecimal tea;

    @Column(name = "tem", precision = 10, scale = 6, nullable = false)
    private BigDecimal tem;

    @Column(name = "monthly_tir", precision = 12, scale = 8, nullable = false)
    private BigDecimal monthlyTir;

    @Column(name = "tcea", precision = 10, scale = 6, nullable = false)
    private BigDecimal tcea;

    @Column(name = "van", precision = 18, scale = 2, nullable = false)
    private BigDecimal van;

    @Column(name = "total_interest", precision = 18, scale = 2, nullable = false)
    private BigDecimal totalInterest;

    @Column(name = "total_cost", precision = 18, scale = 2, nullable = false)
    private BigDecimal totalCost;

    // Client Snapshot
    @Column(name = "client_doc", length = 30)
    private String clientDoc;

    @Column(name = "client_name", length = 150)
    private String clientName;

    @Column(name = "client_email", length = 100)
    private String clientEmail;

    // Property Snapshot
    @Column(name = "property_address", length = 250)
    private String propertyAddress;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", length = 20, nullable = false)
    private QuotationStatus status;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @OneToMany(mappedBy = "quotation", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<InstallmentJpaEntity> installments = new ArrayList<>();

    public void addInstallment(InstallmentJpaEntity installment) {
        installments.add(installment);
        installment.setQuotation(this);
    }
}
