package com.fidiacorp.credicasa.pricing.application.dto.request;
import com.fidiacorp.credicasa.shared.money.Currency;

import com.fidiacorp.credicasa.pricing.domain.model.GracePeriodType;
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

        @Schema(description = "Tasa anual decimal (0.085 = 8.5%): TEA si rateType=TEA (por defecto) o TNA si rateType=TNA; no se aceptan porcentajes", example = "0.085", requiredMode = Schema.RequiredMode.REQUIRED)
        @NotNull(message = "La tasa anual es obligatoria")
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
        ClientProfileDto clientProfile,

        @Schema(description = "US19: tipo de tasa ingresada en annualInterestRate (TEA | TNA). Por defecto TEA.", example = "TNA")
        com.fidiacorp.credicasa.pricing.domain.model.RateType rateType,

        @Schema(description = "US19: periodos de capitalización por año de la TNA (p. ej. 12 mensual o 360 diaria). Obligatorio con TNA.", example = "360")
        @Min(value = 1, message = "La capitalización mínima es 1 periodo por año")
        @Max(value = 365, message = "La capitalización máxima es 365 periodos por año")
        Integer compoundingPeriodsPerYear
) {
    /** Compatibilidad con clientes previos a US19 (TEA implícita). */
    public SimulationRequestDto(BigDecimal propertyValue, BigDecimal loanAmount, Currency currency, Integer termYears, Integer termMonths,
                                BigDecimal annualInterestRate, GracePeriodType gracePeriodType, Integer gracePeriodMonths,
                                BigDecimal balloonPaymentAmount, Integer balloonPaymentMonth, BigDecimal lifeInsuranceRate,
                                BigDecimal propertyInsuranceRate, BigDecimal monthlyAdminFee, BigDecimal discountRateAnnual,
                                LocalDate startDate, String propertyAddress, ClientProfileDto clientProfile) {
        this(propertyValue, loanAmount, currency, termYears, termMonths, annualInterestRate, gracePeriodType, gracePeriodMonths,
                balloonPaymentAmount, balloonPaymentMonth, lifeInsuranceRate, propertyInsuranceRate, monthlyAdminFee, discountRateAnnual,
                startDate, propertyAddress, clientProfile, null, null);
    }

    /** US19: TNA → TEA exacta (DECIMAL128, 8 decimales) o la TEA tal cual. */
    public com.fidiacorp.credicasa.pricing.domain.model.RateTerms resolveRateTerms() {
        if (rateType == com.fidiacorp.credicasa.pricing.domain.model.RateType.TNA) {
            if (compoundingPeriodsPerYear == null)
                throw new IllegalArgumentException("compoundingPeriodsPerYear es obligatorio cuando rateType=TNA.");
            return com.fidiacorp.credicasa.pricing.domain.model.RateTerms.nominal(annualInterestRate, compoundingPeriodsPerYear);
        }
        return com.fidiacorp.credicasa.pricing.domain.model.RateTerms.effective(annualInterestRate);
    }

    public int resolveTotalMonths() {
        if(termMonths!=null && termYears!=null && termMonths!=termYears*12)
            throw new IllegalArgumentException("termMonths y termYears deben representar el mismo plazo.");
        if(termMonths!=null)return termMonths;
        return termYears==null?240:termYears*12;
    }
    public BigDecimal resolveTeaNormalized() {return resolveRateTerms().effectiveAnnualRate();}
}
