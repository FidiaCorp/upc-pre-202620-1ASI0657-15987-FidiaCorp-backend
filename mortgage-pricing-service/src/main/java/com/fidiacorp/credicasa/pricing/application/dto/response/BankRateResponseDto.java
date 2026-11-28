package com.fidiacorp.credicasa.pricing.application.dto.response;
import com.fidiacorp.credicasa.shared.money.Currency;

import io.swagger.v3.oas.annotations.media.Schema;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Schema(description = "Tasa referencial de mercado SBS / Entidad Financiera")
public record BankRateResponseDto(
        @Schema(description = "Código identificador de la entidad financiera", example = "BCP")
        String bankCode,

        @Schema(description = "Nombre comercial de la entidad", example = "Banco de Crédito del Perú")
        String bankName,

        @Schema(description = "Tasa mínima referencial TEA", example = "0.0790")
        BigDecimal minTea,

        @Schema(description = "Tasa máxima referencial TEA", example = "0.1050")
        BigDecimal maxTea,

        @Schema(description = "Tasa promedio ponderada del mercado", example = "0.0885")
        BigDecimal averageTea,

        @Schema(description = "Moneda de la tasa", example = "PEN")
        Currency currency,

        @Schema(description = "Nombre del producto hipotecario", example = "Crédito Hipotecario Tradicional")
        String productName,

        @Schema(description = "Plazo máximo financiable en meses", example = "300")
        Integer maxTermMonths,

        @Schema(description = "Porcentaje máximo de financiamiento sobre el inmueble (LTV)", example = "0.90")
        BigDecimal maxLtv,

        @Schema(description = "Fecha de última actualización del benchmark SBS")
        LocalDateTime updatedAt,

        @Schema(description = "QA-02: origen del dato — LIVE (proveedor externo) o FALLBACK_CACHED (caché SBS con circuito abierto)", example = "LIVE")
        String dataSource,

        @Schema(description = "QA-02: true cuando se responde desde la caché SBS de respaldo", example = "false")
        boolean fallbackActive,

        @Schema(description = "Fecha de corte de las tasas informadas", example = "2026-11-24")
        java.time.LocalDate asOfDate
) {
}
