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
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDate;

@Entity
@Table(name = "installments")
@Getter
@Setter
@NoArgsConstructor
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

    @Column(name = "initial_balance", precision = 18, scale = 2, nullable = false)
    private BigDecimal initialBalance;

    @Column(name = "interest", precision = 18, scale = 2, nullable = false)
    private BigDecimal interest;

    @Column(name = "principal_amortization", precision = 18, scale = 2, nullable = false)
    private BigDecimal principalAmortization;

    @Column(name = "installment_amount", precision = 18, scale = 2, nullable = false)
    private BigDecimal installmentAmount;

    @Column(name = "life_insurance", precision = 18, scale = 2, nullable = false)
    private BigDecimal lifeInsurance;

    @Column(name = "property_insurance", precision = 18, scale = 2, nullable = false)
    private BigDecimal propertyInsurance;

    @Column(name = "admin_fee", precision = 18, scale = 2, nullable = false)
    private BigDecimal adminFee;

    @Column(name = "total_payment", precision = 18, scale = 2, nullable = false)
    private BigDecimal totalPayment;

    @Column(name = "final_balance", precision = 18, scale = 2, nullable = false)
    private BigDecimal finalBalance;

    @Enumerated(EnumType.STRING)
    @Column(name = "grace_type", length = 20, nullable = false)
    private GracePeriodType graceType;
}
