package com.fidiacorp.credicasa.pricing.application.dto.response;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fidiacorp.credicasa.shared.json.PlainDecimalSerializer;
import io.swagger.v3.oas.annotations.media.Schema;

import java.math.BigDecimal;
import java.util.List;

@Schema(description = "US20: indicadores de un escenario comparado")
public record ScenarioResultDto(String label, RateTermsDto rate,
        @JsonSerialize(using = PlainDecimalSerializer.class) BigDecimal loanAmount, int termMonths, int effectiveTermMonths,
        @JsonSerialize(using = PlainDecimalSerializer.class) BigDecimal monthlyInstallment,
        @JsonSerialize(using = PlainDecimalSerializer.class) BigDecimal tea,
        @JsonSerialize(using = PlainDecimalSerializer.class) BigDecimal tem,
        @JsonSerialize(using = PlainDecimalSerializer.class) BigDecimal tcea,
        @JsonSerialize(using = PlainDecimalSerializer.class) BigDecimal monthlyTir,
        @JsonSerialize(using = PlainDecimalSerializer.class) BigDecimal van,
        @JsonSerialize(using = PlainDecimalSerializer.class) BigDecimal totalInterest,
        @JsonSerialize(using = PlainDecimalSerializer.class) BigDecimal totalCost,
        ExtraordinaryPaymentApplied extraordinaryPayment,
        @JsonSerialize(using = PlainDecimalSerializer.class) BigDecimal interestSavings,
        @JsonSerialize(using = PlainDecimalSerializer.class) BigDecimal finalBalance,
        List<InstallmentDto> schedule) {
    public record ExtraordinaryPaymentApplied(int afterInstallment, @JsonSerialize(using = PlainDecimalSerializer.class) BigDecimal amount,
                                              String strategy) {}
}
