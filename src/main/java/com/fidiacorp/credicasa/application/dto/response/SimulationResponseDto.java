package com.fidiacorp.credicasa.application.dto.response;

import com.fidiacorp.credicasa.domain.model.Currency;
import com.fidiacorp.credicasa.domain.model.QuotationStatus;
import io.swagger.v3.oas.annotations.media.Schema;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Schema(description = "Resultado completo de la simulación de crédito hipotecario")
public record SimulationResponseDto(
        @Schema(description = "Identificador único de la cotización generada", example = "a5e8c1b4-2e9f-4318-8f81-58d348a5293e")
        UUID quotationId,

        @Schema(description = "Moneda del préstamo", example = "PEN")
        Currency currency,

        @Schema(description = "Monto del crédito simulado", example = "200000.00")
        BigDecimal loanAmount,

        @Schema(description = "Valor del inmueble", example = "250000.00")
        BigDecimal propertyValue,

        @Schema(description = "Plazo total en meses", example = "240")
        int termMonths,

        @Schema(description = "Resumen de cuota mensual ordinaria estimada", example = "1884.50")
        BigDecimal estimatedMonthlyInstallment,

        @Schema(description = "Métricas financieras (TEA, TEM, TIR, TCEA, VAN, Totales)")
        FinancialMetricsDto metrics,

        @Schema(description = "Estado de la cotización", example = "ISSUED")
        QuotationStatus status,

        @Schema(description = "Cronograma completo detallado cuota a cuota")
        List<InstallmentDto> schedule,

        @Schema(description = "Fecha y hora de emisión")
        LocalDateTime createdAt
) {
}
