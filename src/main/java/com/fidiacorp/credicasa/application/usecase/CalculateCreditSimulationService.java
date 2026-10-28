package com.fidiacorp.credicasa.application.usecase;

import com.fidiacorp.credicasa.domain.model.Account;
import com.fidiacorp.credicasa.domain.model.GracePeriodType;
import com.fidiacorp.credicasa.domain.model.QuotationNotFoundException;
import com.fidiacorp.credicasa.domain.ports.out.AccountRepositoryPort;
import java.time.Clock;
import java.time.LocalDate;
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
    private final AccountRepositoryPort accounts;
    private final Clock clock;

    public CalculateCreditSimulationService(FrenchAmortizationEngine amortizationEngine,
                                            QuotationRepositoryPort quotationRepositoryPort,AccountRepositoryPort accounts,Clock clock) {
        this.amortizationEngine = amortizationEngine;
        this.quotationRepositoryPort = quotationRepositoryPort;
        this.accounts=accounts;this.clock=clock;
    }

    @Override
    @Transactional
    public Quotation calculateAndIssue(CalculateScheduleCommand command) {
        if(command.ownerId()==null)throw new IllegalArgumentException("El propietario autenticado es obligatorio.");
        Quotation quotation = amortizationEngine.generateQuotation(command);
        return quotationRepositoryPort.save(quotation);
    }

    @Transactional
    public SimulationResponseDto simulateAndSave(UUID ownerId,SimulationRequestDto request) {
        CalculateScheduleCommand command = mapDtoToCommand(ownerId,request);
        Quotation savedQuotation = calculateAndIssue(command);
        return mapToResponseDto(savedQuotation);
    }

    @Transactional(readOnly = true)
    public SimulationResponseDto getById(UUID ownerId,UUID id) {
        Quotation quotation = quotationRepositoryPort.findByIdAndOwnerId(id,ownerId)
                .orElseThrow(QuotationNotFoundException::new);
        return mapToResponseDto(quotation);
    }

    private CalculateScheduleCommand mapDtoToCommand(UUID ownerId,SimulationRequestDto request) {
        int months=request.resolveTotalMonths();
        GracePeriod grace=GracePeriod.of(request.gracePeriodType()==null?GracePeriodType.NONE:request.gracePeriodType(),
            request.gracePeriodMonths()==null?0:request.gracePeriodMonths());
        BalloonPayment balloon=BalloonPayment.of(request.balloonPaymentMonth()==null?0:request.balloonPaymentMonth(),
            request.balloonPaymentAmount()==null?BigDecimal.ZERO:request.balloonPaymentAmount());
        Account account=accounts.findById(ownerId).orElseThrow(QuotationNotFoundException::new);
        ClientProfile client=new ClientProfile(account.id(),null,account.firstName()+" "+account.lastName(),account.email(),BigDecimal.ZERO,null);
        BigDecimal propertyValue=request.propertyValue()==null?request.loanAmount():request.propertyValue();
        PropertySnapshot property=new PropertySnapshot(UUID.randomUUID(),propertyValue,request.currency(),
            request.propertyAddress()==null?"Inmueble de referencia":request.propertyAddress());
        return new CalculateScheduleCommand(request.loanAmount(),request.currency(),months,request.resolveTeaNormalized(),grace,balloon,
            request.lifeInsuranceRate(),request.propertyInsuranceRate(),propertyValue,request.monthlyAdminFee(),
            request.startDate()==null?LocalDate.now(clock):request.startDate(),client,property,request.discountRateAnnual(),ownerId);
    }

    public SimulationResponseDto mapToResponseDto(Quotation quotation) {
        List<InstallmentDto> installmentDtos = quotation.getSchedule().stream()
                .map(this::mapInstallmentToDto)
                .collect(Collectors.toList());

        FinancialMetrics metrics = quotation.getMetrics();
        FinancialMetricsDto metricsDto = new FinancialMetricsDto(
                metrics.getTea().setScale(8),
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
                metrics.getDiscountRateUsed()==null?null:metrics.getDiscountRateUsed().setScale(8)
        );

        BigDecimal estimatedMonthly=quotation.getSchedule().stream().filter(i -> i.getGraceType()==GracePeriodType.NONE)
            .findFirst().map(Installment::getTotalPayment).orElse(BigDecimal.ZERO);

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
