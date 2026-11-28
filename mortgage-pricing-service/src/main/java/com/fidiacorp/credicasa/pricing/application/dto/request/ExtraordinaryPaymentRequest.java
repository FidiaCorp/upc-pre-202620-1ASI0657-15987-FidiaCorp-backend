package com.fidiacorp.credicasa.pricing.application.dto.request;

import com.fidiacorp.credicasa.pricing.domain.model.PrepaymentStrategy;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;

@Schema(description = "US20: pago extraordinario opcional aplicado junto con la cuota indicada")
public record ExtraordinaryPaymentRequest(
        @Schema(example = "24") @NotNull(message = "La cuota del prepago es obligatoria") @Min(value = 1, message = "El prepago aplica desde la cuota 1") @Max(value = 359, message = "Cuota de prepago fuera de rango") Integer afterInstallment,
        @Schema(type = "string", example = "20000.00") @NotNull(message = "El importe del prepago es obligatorio") @DecimalMin(value = "0.01", message = "El prepago debe ser positivo") @Digits(integer = 16, fraction = 2, message = "Máximo 2 decimales") BigDecimal amount,
        @Schema(example = "REDUCE_TERM") @NotNull(message = "La estrategia del prepago es obligatoria") PrepaymentStrategy strategy) {
}
