package com.fidiacorp.credicasa.pricing.application.dto.response;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fidiacorp.credicasa.pricing.domain.model.RateTerms;
import com.fidiacorp.credicasa.shared.json.PlainDecimalSerializer;
import io.swagger.v3.oas.annotations.media.Schema;

import java.math.BigDecimal;

@Schema(description = "US19: tasa ingresada y TEA efectiva equivalente usada por el motor")
public record RateTermsDto(
        @Schema(example = "TNA") String rateType,
        @Schema(type = "string", example = "0.12") @JsonSerialize(using = PlainDecimalSerializer.class) BigDecimal inputRate,
        @Schema(example = "360") Integer compoundingPeriodsPerYear,
        @Schema(type = "string", example = "0.12747431") @JsonSerialize(using = PlainDecimalSerializer.class) BigDecimal effectiveAnnualRate) {
    public static RateTermsDto from(RateTerms terms) {
        return new RateTermsDto(terms.rateType().name(), terms.inputRate(), terms.compoundingPeriodsPerYear(), terms.effectiveAnnualRate());
    }
}
