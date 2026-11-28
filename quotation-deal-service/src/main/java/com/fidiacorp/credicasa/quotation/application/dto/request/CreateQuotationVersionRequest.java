package com.fidiacorp.credicasa.quotation.application.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;

import java.math.BigDecimal;

/** US21: nuevos términos (opcionales: se heredan de la última versión) y vigencia en días. */
@Schema(description = "US21: términos de la nueva versión inmutable; los campos omitidos se heredan de la última versión")
public record CreateQuotationVersionRequest(
        @Schema(type = "string", example = "190000.00") @DecimalMin("0.01") @Digits(integer = 16, fraction = 2) BigDecimal loanAmount,
        @Schema(type = "string", example = "0.0825") @DecimalMin(value = "0", inclusive = false) @DecimalMax("1") @Digits(integer = 1, fraction = 8) BigDecimal annualInterestRate,
        @Schema(example = "240") @Min(1) @Max(360) Integer termMonths,
        @Schema(example = "15") @Min(1) @Max(90) Integer validityDays) {
}
