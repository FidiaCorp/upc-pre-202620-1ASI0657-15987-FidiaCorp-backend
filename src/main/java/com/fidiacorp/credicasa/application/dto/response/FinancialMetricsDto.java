package com.fidiacorp.credicasa.application.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;

import java.math.BigDecimal;

@Schema(description = "Métricas financieras calculadas desde la perspectiva del deudor")
public record FinancialMetricsDto(
        @Schema(description = "Tasa Efectiva Anual pactada (TEA)", example = "0.085000")
        BigDecimal tea,

        @Schema(description = "Tasa Efectiva Mensual equivalente (TEM = (1+TEA)^(30/360) - 1)", example = "0.006818")
        BigDecimal tem,

        @Schema(description = "Tasa Interna de Retorno periódica mensual de los flujos del deudor (TIR)", example = "0.007624")
        BigDecimal monthlyTir,

        @Schema(description = "Tasa de Costo Efectivo Anual real que incluye seguros y cargos (TCEA = (1+TIR)^12 - 1)", example = "0.095420")
        BigDecimal tcea,

        @Schema(description = "Valor Actual Neto de los desembolsos y pagos del cliente descontados al COK", example = "-14250.30")
        BigDecimal van,

        @Schema(description = "Total acumulado de intereses a pagar durante el crédito", example = "210880.00")
        BigDecimal totalInterest,

        @Schema(description = "Total de capital amortizado (igual al monto del préstamo)", example = "200000.00")
        BigDecimal totalPrincipal,

        @Schema(description = "Total acumulado de seguro de desgravamen", example = "14200.00")
        BigDecimal totalLifeInsurance,

        @Schema(description = "Total acumulado de seguro de inmueble multirriesgo", example = "15000.00")
        BigDecimal totalPropertyInsurance,

        @Schema(description = "Total acumulado de portes y gastos administrativos", example = "2400.00")
        BigDecimal totalAdminFee,

        @Schema(description = "Costo financiero total desembolsado por el cliente a lo largo del crédito", example = "442480.00")
        BigDecimal totalCost,

        @Schema(description = "Tasa de descuento anual utilizada para el cálculo del VAN", example = "0.085000")
        BigDecimal discountRateUsed
) {
}
