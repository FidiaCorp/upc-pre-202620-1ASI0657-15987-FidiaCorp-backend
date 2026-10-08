package com.fidiacorp.credicasa.application.dto.response;

import com.fidiacorp.credicasa.domain.model.GracePeriodType;
import io.swagger.v3.oas.annotations.media.Schema;

import java.math.BigDecimal;
import java.time.LocalDate;

@Schema(description = "Detalle del cronograma de pagos para una cuota específica")
public record InstallmentDto(
        @Schema(description = "Número correlativo de cuota (1 a N)", example = "1")
        int installmentNumber,

        @Schema(description = "Fecha de vencimiento de la cuota", example = "2026-12-01")
        LocalDate dueDate,

        @Schema(description = "Saldo inicial antes de amortizar en este período", example = "200000.00")
        BigDecimal initialBalance,

        @Schema(description = "Interés generado en el período (Saldo * TEM)", example = "1363.68")
        BigDecimal interest,

        @Schema(description = "Amortización de capital del período", example = "348.32")
        BigDecimal principalAmortization,

        @Schema(description = "Cuota pura (Amortización + Interés)", example = "1712.00")
        BigDecimal installmentAmount,

        @Schema(description = "Seguro de desgravamen del período", example = "100.00")
        BigDecimal lifeInsurance,

        @Schema(description = "Seguro de inmueble multirriesgo del período", example = "62.50")
        BigDecimal propertyInsurance,

        @Schema(description = "Gastos administrativos y portes", example = "10.00")
        BigDecimal adminFee,

        @Schema(description = "Cuota total a pagar por el cliente (Cuota pura + Seguros + Gastos)", example = "1884.50")
        BigDecimal totalPayment,

        @Schema(description = "Saldo final restante al término del período. La última cuota liquida a 0.00 exacto", example = "199651.68")
        BigDecimal finalBalance,

        @Schema(description = "Tipo de gracia activa en la cuota (NONE, PARTIAL, TOTAL)", example = "NONE")
        GracePeriodType graceType
) {
}
