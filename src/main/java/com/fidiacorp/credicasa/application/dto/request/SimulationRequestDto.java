package com.fidiacorp.credicasa.application.dto.request;

import com.fidiacorp.credicasa.domain.model.Currency;
import com.fidiacorp.credicasa.domain.model.GracePeriodType;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.*;

import java.math.BigDecimal;
import java.time.LocalDate;

@Schema(description = "Solicitud de simulación de crédito hipotecario con método francés")
public record SimulationRequestDto(
        @Schema(description = "Valor comercial del inmueble", example = "250000.00")
        @Digits(integer=16,fraction=2)
        @Positive(message = "El valor del inmueble debe ser positivo")
        BigDecimal propertyValue,

        @Schema(description = "Monto del préstamo hipotecario solicitado", example = "200000.00", requiredMode = Schema.RequiredMode.REQUIRED)
        @NotNull(message = "El monto del préstamo es obligatorio")
        @Digits(integer=16,fraction=2)
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

        @Schema(description = "Tasa Efectiva Anual (TEA) decimal: 0.085 para 8.5%; no se aceptan porcentajes", example = "0.085", requiredMode = Schema.RequiredMode.REQUIRED)
        @NotNull(message = "La tasa TEA es obligatoria")
        @DecimalMax("1") @Digits(integer=1,fraction=8)
        @Positive(message = "La tasa TEA debe ser positiva")
        BigDecimal annualInterestRate,

        @Schema(description = "Tipo de período de gracia (NONE, PARTIAL, TOTAL)", example = "NONE")
        GracePeriodType gracePeriodType,

        @Schema(description = "Número de meses de gracia (0 a 36)", example = "0")
        @Min(value = 0, message = "Los meses de gracia no pueden ser negativos")
        @Max(36) Integer gracePeriodMonths,

        @Schema(description = "Monto de cuota extraordinaria / balón programada al vencimiento", example = "0.00")
        @PositiveOrZero @Digits(integer=16,fraction=2) BigDecimal balloonPaymentAmount,

        @Schema(description = "Mes de pago de la cuota balón", example = "0")
        @Min(0) @Max(360) Integer balloonPaymentMonth,

        @Schema(description = "Tasa mensual de seguro de desgravamen en decimal (por defecto 0)", example = "0.0005")
        @DecimalMin("0") @DecimalMax("1") @Digits(integer=1,fraction=8) BigDecimal lifeInsuranceRate,

        @Schema(description = "Tasa mensual de seguro de inmueble multirriesgo en decimal (por defecto 0)", example = "0.00025")
        @DecimalMin("0") @DecimalMax("1") @Digits(integer=1,fraction=8) BigDecimal propertyInsuranceRate,

        @Schema(description = "Gastos de administración y portes mensuales fijos", example = "10.00")
        @PositiveOrZero @Digits(integer=16,fraction=2) BigDecimal monthlyAdminFee,

        @Schema(description = "Tasa de descuento anual (COK) para cálculo del VAN. Si no se indica se usa la TEA", example = "0.085")
        @DecimalMin("0") @DecimalMax("1") @Digits(integer=1,fraction=8) BigDecimal discountRateAnnual,

        @Schema(description = "Fecha de desembolso o inicio del crédito (YYYY-MM-DD)", example = "2026-11-01")
        LocalDate startDate,

        @Schema(description = "Dirección referencial del inmueble", example = "Av. Javier Prado Este 2450, San Borja, Lima")
        @Size(max=250) String propertyAddress,

        @Schema(description = "Perfil del cliente solicitante")
        @Valid
        ClientProfileDto clientProfile
) {
    public int resolveTotalMonths() {
        if(termMonths!=null && termYears!=null && termMonths!=termYears*12)
            throw new IllegalArgumentException("termMonths y termYears deben representar el mismo plazo.");
        if(termMonths!=null)return termMonths;
        return termYears==null?240:termYears*12;
    }
    public BigDecimal resolveTeaNormalized() {return annualInterestRate;}
}
