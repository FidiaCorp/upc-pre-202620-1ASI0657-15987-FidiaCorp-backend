package com.fidiacorp.credicasa.pricing.application.usecase;

import com.fidiacorp.credicasa.pricing.domain.model.GracePeriodType;
import com.fidiacorp.credicasa.pricing.domain.model.QuotationNotFoundException;
import java.time.Clock;
import java.time.LocalDate;
import com.fidiacorp.credicasa.pricing.application.dto.request.SimulationRequestDto;
import com.fidiacorp.credicasa.pricing.application.dto.response.FinancialMetricsDto;
import com.fidiacorp.credicasa.pricing.application.dto.response.InstallmentDto;
import com.fidiacorp.credicasa.pricing.application.dto.response.SimulationResponseDto;
import com.fidiacorp.credicasa.pricing.domain.model.BalloonPayment;
import com.fidiacorp.credicasa.pricing.domain.model.ClientProfile;
import com.fidiacorp.credicasa.pricing.domain.model.FinancialMetrics;
import com.fidiacorp.credicasa.pricing.domain.model.GracePeriod;
import com.fidiacorp.credicasa.pricing.domain.model.Installment;
import com.fidiacorp.credicasa.pricing.domain.model.PropertySnapshot;
import com.fidiacorp.credicasa.pricing.domain.model.Quotation;
import com.fidiacorp.credicasa.pricing.domain.ports.in.CalculateScheduleCommand;
import com.fidiacorp.credicasa.pricing.domain.ports.in.CalculateScheduleUseCase;
import com.fidiacorp.credicasa.pricing.domain.ports.out.QuotationRepositoryPort;
import com.fidiacorp.credicasa.pricing.domain.service.AmortizationStrategy;
import com.fidiacorp.credicasa.pricing.domain.service.ScenarioComparisonEngine;
import com.fidiacorp.credicasa.pricing.domain.model.ExtraordinaryPayment;
import com.fidiacorp.credicasa.pricing.domain.model.RateTerms;
import com.fidiacorp.credicasa.pricing.domain.model.ScenarioOutcome;
import com.fidiacorp.credicasa.pricing.application.dto.request.CompareScenariosRequest;
import com.fidiacorp.credicasa.pricing.application.dto.response.RateTermsDto;
import com.fidiacorp.credicasa.pricing.application.dto.response.ScenarioComparisonResponse;
import com.fidiacorp.credicasa.pricing.application.dto.response.ScenarioResultDto;
import com.fidiacorp.credicasa.pricing.application.dto.response.SimulationPageResponse;
import com.fidiacorp.credicasa.pricing.application.dto.response.SimulationSummaryResponse;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * US11/US19/US20: genera, persiste, lista y compara simulaciones. Depende de la abstracción {@link AmortizationStrategy}
 * (Figura 6). El cliente (nombre/correo) proviene de los claims del JWT: no hay FK ni consulta a identity_db.
 */
@Service
public class CalculateCreditSimulationService implements CalculateScheduleUseCase {
    /** Identidad del solicitante tomada del JWT validado. */
    public record Requester(UUID id, String displayName, String email) {}

    private final AmortizationStrategy amortizationEngine;
    private final ScenarioComparisonEngine comparator;
    private final QuotationRepositoryPort quotationRepositoryPort;
    private final Clock clock;

    public CalculateCreditSimulationService(AmortizationStrategy amortizationEngine, ScenarioComparisonEngine comparator,
                                            QuotationRepositoryPort quotationRepositoryPort, Clock clock) {
        this.amortizationEngine = amortizationEngine;
        this.comparator = comparator;
        this.quotationRepositoryPort = quotationRepositoryPort;
        this.clock = clock;
    }

    @Override
    @Transactional
    public Quotation calculateAndIssue(CalculateScheduleCommand command) {
        if(command.ownerId()==null)throw new IllegalArgumentException("El propietario autenticado es obligatorio.");
        Quotation quotation = amortizationEngine.generateQuotation(command);
        return quotationRepositoryPort.save(quotation);
    }

    @Transactional
    public SimulationResponseDto simulateAndSave(Requester requester,SimulationRequestDto request) {
        RateTerms terms = request.resolveRateTerms();
        CalculateScheduleCommand command = mapDtoToCommand(requester,request,terms);
        if(command.ownerId()==null)throw new IllegalArgumentException("El propietario autenticado es obligatorio.");
        Quotation quotation = amortizationEngine.generateQuotation(command);
        quotation.assignRateTerms(terms);
        return mapToResponseDto(quotationRepositoryPort.save(quotation));
    }

    /** US20: historial propio paginado. */
    @Transactional(readOnly = true)
    public SimulationPageResponse listOwn(UUID ownerId, int page, int size) {
        if (page < 0) throw new IllegalArgumentException("La página no puede ser negativa.");
        if (size < 1 || size > 50) throw new IllegalArgumentException("El tamaño de página debe estar entre 1 y 50.");
        var result = quotationRepositoryPort.findSummariesByOwner(ownerId, page, size);
        int pages = (int) Math.ceil(result.totalElements() / (double) size);
        return new SimulationPageResponse(result.items().stream().map(SimulationSummaryResponse::from).toList(), page, size,
                result.totalElements(), pages);
    }

    /** US20: compara 2–3 escenarios con prepago opcional; nada se persiste. */
    public ScenarioComparisonResponse compare(Requester requester, CompareScenariosRequest request) {
        var inputs = request.scenarios().stream().map(s -> {
            RateTerms terms = s.simulation().resolveRateTerms();
            var ep = s.extraordinaryPayment();
            return new ScenarioComparisonEngine.ScenarioInput(s.label(), mapDtoToCommand(requester, s.simulation(), terms), terms,
                    ep == null ? null : new ExtraordinaryPayment(ep.afterInstallment(), ep.amount(), ep.strategy()));
        }).toList();
        var comparison = comparator.compare(inputs);
        return new ScenarioComparisonResponse(comparison.scenarios().stream().map(this::toResult).toList(), comparison.bestByTcea(),
                comparison.bestByTotalInterest(), comparison.bestByMonthlyInstallment(), comparison.bestByVan(), comparison.bestByTotalCost());
    }

    private ScenarioResultDto toResult(ScenarioOutcome o) {
        FinancialMetrics m = o.metrics();
        var ep = o.extraordinaryPayment();
        var rows = o.schedule();
        return new ScenarioResultDto(o.label(), RateTermsDto.from(o.rateTerms()), o.quotation().getLoanAmount(), o.quotation().getTermMonths(),
                o.effectiveTermMonths(), o.monthlyInstallment(), m.getTea(), m.getTem(), m.getTcea(), m.getMonthlyTir(), m.getVan(),
                m.getTotalInterest(), m.getTotalCost(),
                ep == null ? null : new ScenarioResultDto.ExtraordinaryPaymentApplied(ep.afterInstallment(), ep.amount().setScale(2), ep.strategy().name()),
                o.interestSavings(), rows.get(rows.size() - 1).getFinalBalance(), rows.stream().map(this::mapInstallmentToDto).toList());
    }

    @Transactional(readOnly = true)
    public SimulationResponseDto getById(UUID ownerId,UUID id) {
        Quotation quotation = quotationRepositoryPort.findByIdAndOwnerId(id,ownerId)
                .orElseThrow(QuotationNotFoundException::new);
        return mapToResponseDto(quotation);
    }

    private CalculateScheduleCommand mapDtoToCommand(Requester requester,SimulationRequestDto request,RateTerms terms) {
        UUID ownerId=requester.id();
        int months=request.resolveTotalMonths();
        if(request.currency()==null)throw new IllegalArgumentException("La moneda es obligatoria.");
        GracePeriod grace=GracePeriod.of(request.gracePeriodType()==null?GracePeriodType.NONE:request.gracePeriodType(),
            request.gracePeriodMonths()==null?0:request.gracePeriodMonths());
        BalloonPayment balloon=BalloonPayment.of(request.balloonPaymentMonth()==null?0:request.balloonPaymentMonth(),
            request.balloonPaymentAmount()==null?BigDecimal.ZERO:request.balloonPaymentAmount());
        ClientProfile client=new ClientProfile(ownerId,null,requester.displayName(),requester.email(),BigDecimal.ZERO,null);
        BigDecimal propertyValue=request.propertyValue()==null?request.loanAmount():request.propertyValue();
        PropertySnapshot property=new PropertySnapshot(UUID.randomUUID(),propertyValue,request.currency(),
            request.propertyAddress()==null?"Inmueble de referencia":request.propertyAddress());
        return new CalculateScheduleCommand(request.loanAmount(),request.currency(),months,terms.effectiveAnnualRate(),grace,balloon,
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
                quotation.getCreatedAt(),
                RateTermsDto.from(quotation.getRateTerms())
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
