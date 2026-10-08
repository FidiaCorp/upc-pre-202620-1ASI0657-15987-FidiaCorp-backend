package com.fidiacorp.credicasa.infrastructure.persistence;

import com.fidiacorp.credicasa.domain.model.BalloonPayment;
import com.fidiacorp.credicasa.domain.model.ClientProfile;
import com.fidiacorp.credicasa.domain.model.FinancialMetrics;
import com.fidiacorp.credicasa.domain.model.GracePeriod;
import com.fidiacorp.credicasa.domain.model.Installment;
import com.fidiacorp.credicasa.domain.model.PropertySnapshot;
import com.fidiacorp.credicasa.domain.model.Quotation;
import com.fidiacorp.credicasa.domain.ports.out.QuotationRepositoryPort;
import com.fidiacorp.credicasa.infrastructure.persistence.entity.InstallmentJpaEntity;
import com.fidiacorp.credicasa.infrastructure.persistence.entity.QuotationJpaEntity;
import com.fidiacorp.credicasa.infrastructure.persistence.repository.QuotationJpaRepository;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

@Component
public class QuotationRepositoryAdapter implements QuotationRepositoryPort {

    private final QuotationJpaRepository jpaRepository;

    public QuotationRepositoryAdapter(QuotationJpaRepository jpaRepository) {
        this.jpaRepository = jpaRepository;
    }

    @Override
    @Transactional
    public Quotation save(Quotation quotation) {
        QuotationJpaEntity entity = toEntity(quotation);
        QuotationJpaEntity savedEntity = jpaRepository.save(entity);
        return toDomain(savedEntity);
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<Quotation> findById(UUID id) {
        return jpaRepository.findByIdWithInstallments(id).map(this::toDomain);
    }

    @Override
    @Transactional(readOnly = true)
    public List<Quotation> findAll() {
        return jpaRepository.findAll().stream()
                .map(this::toDomain)
                .collect(Collectors.toList());
    }

    private QuotationJpaEntity toEntity(Quotation domain) {
        QuotationJpaEntity entity = new QuotationJpaEntity();
        entity.setId(domain.getId());
        entity.setLoanAmount(domain.getLoanAmount());
        entity.setCurrency(domain.getCurrency());
        entity.setTermMonths(domain.getTermMonths());
        entity.setAnnualInterestRate(domain.getAnnualInterestRate());
        entity.setGracePeriodType(domain.getGracePeriod().getType());
        entity.setGracePeriodMonths(domain.getGracePeriod().getMonths());

        if (domain.getBalloonPayment() != null && domain.getBalloonPayment().isConfigured()) {
            entity.setBalloonAmount(domain.getBalloonPayment().getAmount());
            entity.setBalloonMonth(domain.getBalloonPayment().getInstallmentNumber());
        }

        entity.setLifeInsuranceRate(domain.getLifeInsuranceRate());
        entity.setPropertyInsuranceRate(domain.getPropertyInsuranceRate());
        entity.setPropertyValue(domain.getPropertyValue());
        entity.setMonthlyAdminFee(domain.getMonthlyAdminFee());
        entity.setStartDate(domain.getStartDate());

        FinancialMetrics metrics = domain.getMetrics();
        if (metrics != null) {
            entity.setTea(metrics.getTea());
            entity.setTem(metrics.getTem());
            entity.setMonthlyTir(metrics.getMonthlyTir());
            entity.setTcea(metrics.getTcea());
            entity.setVan(metrics.getVan());
            entity.setTotalInterest(metrics.getTotalInterest());
            entity.setTotalCost(metrics.getTotalCost());
        }

        if (domain.getClientProfile() != null) {
            entity.setClientDoc(domain.getClientProfile().getDocumentNumber());
            entity.setClientName(domain.getClientProfile().getFullName());
            entity.setClientEmail(domain.getClientProfile().getEmail());
        }

        if (domain.getPropertySnapshot() != null) {
            entity.setPropertyAddress(domain.getPropertySnapshot().getAddress());
        }

        entity.setStatus(domain.getStatus());
        entity.setCreatedAt(domain.getCreatedAt());

        if (domain.getSchedule() != null) {
            for (Installment inst : domain.getSchedule()) {
                InstallmentJpaEntity instEntity = new InstallmentJpaEntity();
                instEntity.setInstallmentNumber(inst.getInstallmentNumber());
                instEntity.setDueDate(inst.getDueDate());
                instEntity.setInitialBalance(inst.getInitialBalance());
                instEntity.setInterest(inst.getInterest());
                instEntity.setPrincipalAmortization(inst.getPrincipalAmortization());
                instEntity.setInstallmentAmount(inst.getInstallmentAmount());
                instEntity.setLifeInsurance(inst.getLifeInsurance());
                instEntity.setPropertyInsurance(inst.getPropertyInsurance());
                instEntity.setAdminFee(inst.getAdminFee());
                instEntity.setTotalPayment(inst.getTotalPayment());
                instEntity.setFinalBalance(inst.getFinalBalance());
                instEntity.setGraceType(inst.getGraceType());
                entity.addInstallment(instEntity);
            }
        }

        return entity;
    }

    private Quotation toDomain(QuotationJpaEntity entity) {
        GracePeriod gracePeriod = GracePeriod.of(entity.getGracePeriodType(), entity.getGracePeriodMonths());
        BalloonPayment balloonPayment = entity.getBalloonAmount() != null && entity.getBalloonMonth() != null
                ? BalloonPayment.of(entity.getBalloonMonth(), entity.getBalloonAmount())
                : BalloonPayment.none();

        ClientProfile clientProfile = null;
        if (entity.getClientDoc() != null) {
            clientProfile = new ClientProfile(
                    UUID.randomUUID(),
                    entity.getClientDoc(),
                    entity.getClientName(),
                    entity.getClientEmail(),
                    BigDecimal.ZERO,
                    null
            );
        }

        PropertySnapshot propertySnapshot = new PropertySnapshot(
                UUID.randomUUID(),
                entity.getPropertyValue(),
                entity.getCurrency(),
                entity.getPropertyAddress()
        );

        List<Installment> schedule = new ArrayList<>();
        BigDecimal sumLife = BigDecimal.ZERO;
        BigDecimal sumProp = BigDecimal.ZERO;
        BigDecimal sumAdmin = BigDecimal.ZERO;
        BigDecimal sumPrinc = BigDecimal.ZERO;

        if (entity.getInstallments() != null) {
            for (InstallmentJpaEntity ie : entity.getInstallments()) {
                schedule.add(new Installment(
                        ie.getInstallmentNumber(),
                        ie.getDueDate(),
                        ie.getInitialBalance(),
                        ie.getInterest(),
                        ie.getPrincipalAmortization(),
                        ie.getInstallmentAmount(),
                        ie.getLifeInsurance(),
                        ie.getPropertyInsurance(),
                        ie.getAdminFee(),
                        ie.getTotalPayment(),
                        ie.getFinalBalance(),
                        ie.getGraceType()
                ));
                sumLife = sumLife.add(ie.getLifeInsurance());
                sumProp = sumProp.add(ie.getPropertyInsurance());
                sumAdmin = sumAdmin.add(ie.getAdminFee());
                sumPrinc = sumPrinc.add(ie.getPrincipalAmortization());
            }
        }

        FinancialMetrics metrics = new FinancialMetrics(
                entity.getTea(),
                entity.getTem(),
                entity.getMonthlyTir(),
                entity.getTcea(),
                entity.getVan(),
                entity.getTotalInterest(),
                sumPrinc,
                sumLife,
                sumProp,
                sumAdmin,
                entity.getTotalCost(),
                entity.getTea()
        );

        return new Quotation(
                entity.getId(),
                entity.getLoanAmount(),
                entity.getCurrency(),
                entity.getTermMonths(),
                entity.getAnnualInterestRate(),
                gracePeriod,
                balloonPayment,
                entity.getLifeInsuranceRate(),
                entity.getPropertyInsuranceRate(),
                entity.getPropertyValue(),
                entity.getMonthlyAdminFee(),
                entity.getStartDate(),
                clientProfile,
                propertySnapshot,
                schedule,
                metrics,
                entity.getStatus(),
                entity.getCreatedAt()
        );
    }
}
