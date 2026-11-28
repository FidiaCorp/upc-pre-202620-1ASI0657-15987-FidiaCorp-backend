package com.fidiacorp.credicasa.quotation.application.dto.response;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fidiacorp.credicasa.shared.json.PlainDecimalSerializer;
import com.fidiacorp.credicasa.shared.money.Currency;
import io.swagger.v3.oas.annotations.media.Schema;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

/** Cronograma calculado por mortgage-pricing-service (mismo contrato JSON que SimulationResponseDto). */
@Schema(name = "SimulationResponseDto", description = "Cronograma francés calculado por mortgage-pricing-service")
@JsonIgnoreProperties(ignoreUnknown = true)
public record OfferScheduleResponse(UUID quotationId, Currency currency, @JsonSerialize(using = PlainDecimalSerializer.class) BigDecimal loanAmount, @JsonSerialize(using = PlainDecimalSerializer.class) BigDecimal propertyValue, int termMonths,
                                    @JsonSerialize(using = PlainDecimalSerializer.class) BigDecimal estimatedMonthlyInstallment, Metrics metrics, String status, List<Row> schedule,
                                    LocalDateTime createdAt, Rate rate) {
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Metrics(@JsonSerialize(using = PlainDecimalSerializer.class) BigDecimal tea, @JsonSerialize(using = PlainDecimalSerializer.class) BigDecimal tem, @JsonSerialize(using = PlainDecimalSerializer.class) BigDecimal monthlyTir, @JsonSerialize(using = PlainDecimalSerializer.class) BigDecimal tcea, @JsonSerialize(using = PlainDecimalSerializer.class) BigDecimal van, @JsonSerialize(using = PlainDecimalSerializer.class) BigDecimal totalInterest,
                          @JsonSerialize(using = PlainDecimalSerializer.class) BigDecimal totalPrincipal, @JsonSerialize(using = PlainDecimalSerializer.class) BigDecimal totalLifeInsurance, @JsonSerialize(using = PlainDecimalSerializer.class) BigDecimal totalPropertyInsurance, @JsonSerialize(using = PlainDecimalSerializer.class) BigDecimal totalAdminFee,
                          @JsonSerialize(using = PlainDecimalSerializer.class) BigDecimal totalCost, @JsonSerialize(using = PlainDecimalSerializer.class) BigDecimal discountRateUsed) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Row(int installmentNumber, LocalDate dueDate, @JsonSerialize(using = PlainDecimalSerializer.class) BigDecimal initialBalance, @JsonSerialize(using = PlainDecimalSerializer.class) BigDecimal interest, @JsonSerialize(using = PlainDecimalSerializer.class) BigDecimal principalAmortization,
                      @JsonSerialize(using = PlainDecimalSerializer.class) BigDecimal installmentAmount, @JsonSerialize(using = PlainDecimalSerializer.class) BigDecimal lifeInsurance, @JsonSerialize(using = PlainDecimalSerializer.class) BigDecimal propertyInsurance, @JsonSerialize(using = PlainDecimalSerializer.class) BigDecimal adminFee, @JsonSerialize(using = PlainDecimalSerializer.class) BigDecimal totalPayment,
                      @JsonSerialize(using = PlainDecimalSerializer.class) BigDecimal finalBalance, String graceType) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Rate(String rateType, @JsonSerialize(using = PlainDecimalSerializer.class) BigDecimal inputRate, Integer compoundingPeriodsPerYear, @JsonSerialize(using = PlainDecimalSerializer.class) BigDecimal effectiveAnnualRate) {}
}
