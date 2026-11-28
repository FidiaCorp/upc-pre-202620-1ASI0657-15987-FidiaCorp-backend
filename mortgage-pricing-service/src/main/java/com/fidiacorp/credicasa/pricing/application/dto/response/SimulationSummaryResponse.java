package com.fidiacorp.credicasa.pricing.application.dto.response;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fidiacorp.credicasa.pricing.domain.model.QuotationSummary;
import com.fidiacorp.credicasa.shared.json.PlainDecimalSerializer;
import com.fidiacorp.credicasa.shared.money.Currency;
import io.swagger.v3.oas.annotations.media.Schema;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

@Schema(description = "US20: simulación propia en el historial")
public record SimulationSummaryResponse(UUID quotationId, LocalDateTime createdAt, Currency currency,
        @JsonSerialize(using = PlainDecimalSerializer.class) BigDecimal loanAmount, int termMonths, String rateType,
        @JsonSerialize(using = PlainDecimalSerializer.class) BigDecimal inputRate,
        @JsonSerialize(using = PlainDecimalSerializer.class) BigDecimal tea,
        @JsonSerialize(using = PlainDecimalSerializer.class) BigDecimal tcea,
        @JsonSerialize(using = PlainDecimalSerializer.class) BigDecimal totalInterest,
        @JsonSerialize(using = PlainDecimalSerializer.class) BigDecimal totalCost, String propertyAddress) {
    public static SimulationSummaryResponse from(QuotationSummary s) {
        return new SimulationSummaryResponse(s.quotationId(), s.createdAt(), s.currency(), s.loanAmount(), s.termMonths(), s.rateType(),
                com.fidiacorp.credicasa.pricing.domain.model.RateTerms.plainRate(s.inputRate()),
                com.fidiacorp.credicasa.pricing.domain.model.RateTerms.canonicalRate(s.tea()), s.tcea(), s.totalInterest(), s.totalCost(), s.propertyAddress());
    }
}
