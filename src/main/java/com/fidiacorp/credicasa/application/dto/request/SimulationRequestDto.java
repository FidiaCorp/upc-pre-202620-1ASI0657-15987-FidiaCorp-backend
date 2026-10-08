package com.fidiacorp.credicasa.application.dto.request;

import com.fidiacorp.credicasa.domain.model.Currency;
import com.fidiacorp.credicasa.domain.model.GracePeriodType;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import java.math.BigDecimal;
import java.time.LocalDate;

@Schema(description = "Solicitud de simulación de crédito hipotecario con método francés")
public record SimulationRequestDto(
        @Schema(description = "Valor comercial del inmueble", example = "250000.00")
        @Positive(message = "El valor del inmueble debe ser positivo")
        BigDecimal propertyValue,

        @Schema(description = "Monto del préstamo hipotecario solicitado", example = "200000.00", requiredMode = Schema.RequiredMode.REQUIRED)
        @NotNull(message = "El monto del préstamo es obligatorio")
        @Positive(message = "El monto del préstamo debe ser positivo")
        BigDecimal loanAmount,

        @Schema(description = "Moneda del préstamo (PEN / USD)", example = "PEN", requiredMode = Schema.RequiredMode.REQUIRED)
        @NotNull(message = "La moneda es obligatoria")
        Currency currency,

        @Schema(description = "Plazo del crédito en años (opcional si se especifica termMonths)", example = "20")
        @Min(value = 1, message = "El plazo mínimo es 1 año")
        @Max(value = 30, message = "El plazo máximo es 30 años")
        Integer termYears,

        @Schema(description = "Plazo del crédito en meses (1 a 360 meses)", example = "240")
        @Min(value = 1, message = "El plazo mínimo es 1 mes")
        @Max(value = 360, message = "El plazo máximo es 360 meses")
        Integer termMonths,

        @Schema(description = "Tasa Efectiva Anual (TEA) pactada en decimal (ej. 0.085 para 8.5%) o porcentaje", example = "0.085", requiredMode = Schema.RequiredMode.REQUIRED)
        @NotNull(message = "La tasa TEA es obligatoria")
        @Positive(message = "La tasa TEA debe ser positiva")
        BigDecimal annualInterestRate,

        @Schema(description = "Tipo de período de gracia (NONE, PARTIAL, TOTAL)", example = "NONE")
        GracePeriodType gracePeriodType,

        @Schema(description = "Número de meses de gracia (0 a 36)", example = "0")
        @Min(value = 0, message = "Los meses de gracia no pueden ser negativos")
        Integer gracePeriodMonths,

        @Schema(description = "Monto de cuota extraordinaria / balón programada al vencimiento", example = "0.00")
        BigDecimal balloonPaymentAmount,

        @Schema(description = "Mes de pago de la cuota balón", example = "0")
        Integer balloonPaymentMonth,

        @Schema(description = "Tasa mensual de seguro de desgravamen en decimal (por defecto 0.0005 = 0.05% mensual)", example = "0.0005")
        BigDecimal lifeInsuranceRate,

        @Schema(description = "Tasa mensual de seguro de inmueble multirriesgo en decimal (por defecto 0.00025 = 0.025% mensual)", example = "0.00025")
        BigDecimal propertyInsuranceRate,

        @Schema(description = "Gastos de administración y portes mensuales fijos", example = "10.00")
        BigDecimal monthlyAdminFee,

        @Schema(description = "Tasa de descuento anual (COK) para cálculo del VAN. Si no se indica se usa la TEA", example = "0.085")
        BigDecimal discountRateAnnual,

        @Schema(description = "Fecha de desembolso o inicio del crédito (YYYY-MM-DD)", example = "2026-11-01")
        LocalDate startDate,

        @Schema(description = "Dirección referencial del inmueble", example = "Av. Javier Prado Este 2450, San Borja, Lima")
        String propertyAddress,

        @Schema(description = "Perfil del cliente solicitante")
        @Valid
        ClientProfileDto clientProfile
) {
    public int resolveTotalMonths() {
        if (termMonths != null && termMonths > 0) {
            return termMonths;
        }
        if (termYears != null && termYears > 0) {
            return termYears * 12;
        }
        return 240; // Default 20 years = 240 months
    }

    public BigDecimal resolveTeaNormalized() {
        // If user sent 8.5 instead of 0.085, normalize to 0.085
        if (annualInterestRate.compareTo(BigDecimal.ONE) > 0) {
            return annualInterestRate.divide(BigDecimal.valueOf(100), 6, java.math.RoundingMode.HALF_EVEN);
        }
        return annualInterestRate;
    }
}
