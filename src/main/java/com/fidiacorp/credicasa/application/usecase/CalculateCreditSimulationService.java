package com.fidiacorp.credicasa.application.usecase;

import com.fidiacorp.credicasa.application.dto.request.ClientProfileDto;
import com.fidiacorp.credicasa.application.dto.request.SimulationRequestDto;
import com.fidiacorp.credicasa.application.dto.response.FinancialMetricsDto;
import com.fidiacorp.credicasa.application.dto.response.InstallmentDto;
import com.fidiacorp.credicasa.application.dto.response.SimulationResponseDto;
import com.fidiacorp.credicasa.domain.model.BalloonPayment;
import com.fidiacorp.credicasa.domain.model.ClientProfile;
import com.fidiacorp.credicasa.domain.model.FinancialMetrics;
import com.fidiacorp.credicasa.domain.model.GracePeriod;
import com.fidiacorp.credicasa.domain.model.Installment;
import com.fidiacorp.credicasa.domain.model.PropertySnapshot;
import com.fidiacorp.credicasa.domain.model.Quotation;
import com.fidiacorp.credicasa.domain.ports.in.CalculateScheduleCommand;
import com.fidiacorp.credicasa.domain.ports.in.CalculateScheduleUseCase;
import com.fidiacorp.credicasa.domain.ports.out.QuotationRepositoryPort;
import com.fidiacorp.credicasa.domain.service.FrenchAmortizationEngine;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
public class CalculateCreditSimulationService implements CalculateScheduleUseCase {

    private final FrenchAmortizationEngine amortizationEngine;
    private final QuotationRepositoryPort quotationRepositoryPort;

    public CalculateCreditSimulationService(FrenchAmortizationEngine amortizationEngine,
                                            QuotationRepositoryPort quotationRepositoryPort) {
        this.amortizationEngine = amortizationEngine;
        this.quotationRepositoryPort = quotationRepositoryPort;
    }

    @Override
    @Transactional
    public Quotation calculateAndIssue(CalculateScheduleCommand command) {
        Quotation quotation = amortizationEngine.generateQuotation(command);
        return quotationRepositoryPort.save(quotation);
    }

    @Transactional
    public SimulationResponseDto simulateAndSave(SimulationRequestDto request) {
        CalculateScheduleCommand command = mapDtoToCommand(request);
        Quotation savedQuotation = calculateAndIssue(command);
        return mapToResponseDto(savedQuotation);
    }

    @Transactional(readOnly = true)
    public SimulationResponseDto getById(UUID id) {
        Quotation quotation = quotationRepositoryPort.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Cotización no encontrada con ID: " + id));
        return mapToResponseDto(quotation);
    }

    private CalculateScheduleCommand mapDtoToCommand(SimulationRequestDto request) {
        int termMonths = request.resolveTotalMonths();
        BigDecimal tea = request.resolveTeaNormalized();

        GracePeriod gracePeriod = request.gracePeriodType() != null && request.gracePeriodMonths() != null
                ? GracePeriod.of(request.gracePeriodType(), request.gracePeriodMonths())
                : GracePeriod.none();

        BalloonPayment balloonPayment = request.balloonPaymentAmount() != null && request.balloonPaymentMonth() != null
                ? BalloonPayment.of(request.balloonPaymentMonth(), request.balloonPaymentAmount())
                : BalloonPayment.none();

        ClientProfile clientProfile = null;
        if (request.clientProfile() != null) {
            ClientProfileDto cp = request.clientProfile();
            clientProfile = new ClientProfile(
                    UUID.randomUUID(),
                    cp.documentNumber(),
                    cp.fullName(),
                    cp.email(),
                    cp.monthlyIncome(),
                    cp.creditScore()
            );
        }

        PropertySnapshot propertySnapshot = new PropertySnapshot(
                UUID.randomUUID(),
                request.propertyValue() != null ? request.propertyValue() : request.loanAmount(),
                request.currency(),
                request.propertyAddress() != null ? request.propertyAddress() : "Inmueble de referencia"
        );

        return new CalculateScheduleCommand(
                request.loanAmount(),
                request.currency(),
                termMonths,
                tea,
                gracePeriod,
                balloonPayment,
                request.lifeInsuranceRate(),
                request.propertyInsuranceRate(),
                request.propertyValue(),
                request.monthlyAdminFee(),
                request.startDate(),
                clientProfile,
                propertySnapshot,
                request.discountRateAnnual()
        );
    }

    public SimulationResponseDto mapToResponseDto(Quotation quotation) {
        List<InstallmentDto> installmentDtos = quotation.getSchedule().stream()
                .map(this::mapInstallmentToDto)
                .collect(Collectors.toList());

        FinancialMetrics metrics = quotation.getMetrics();
        FinancialMetricsDto metricsDto = new FinancialMetricsDto(
                metrics.getTea(),
                metrics.getTem(),
                metrics.getMonthlyTir(),
                metrics.getTcea(),
                metrics.getVan(),
                metrics.getTotalInterest(),
                metrics.getTotalPrincipal(),
                metrics.getTotalLifeInsurance(),
                metrics.getTotalPropertyInsurance(),
                metrics.getTotalAdminFee(),
                metrics.getTotalCost(),
                metrics.getDiscountRateUsed()
        );

        BigDecimal estimatedMonthly = quotation.getSchedule().isEmpty()
                ? BigDecimal.ZERO
                : quotation.getSchedule().get(0).getTotalPayment();

        return new SimulationResponseDto(
                quotation.getId(),
                quotation.getCurrency(),
                quotation.getLoanAmount(),
                quotation.getPropertyValue(),
                quotation.getTermMonths(),
                estimatedMonthly,
                metricsDto,
                quotation.getStatus(),
                installmentDtos,
                quotation.getCreatedAt()
        );
    }

    private InstallmentDto mapInstallmentToDto(Installment inst) {
        return new InstallmentDto(
                inst.getInstallmentNumber(),
                inst.getDueDate(),
                inst.getInitialBalance(),
                inst.getInterest(),
                inst.getPrincipalAmortization(),
                inst.getInstallmentAmount(),
                inst.getLifeInsurance(),
                inst.getPropertyInsurance(),
                inst.getAdminFee(),
                inst.getTotalPayment(),
                inst.getFinalBalance(),
                inst.getGraceType()
        );
    }
}
