package com.fidiacorp.credicasa.pricing.domain.model;

import com.fidiacorp.credicasa.shared.finance.FinancialMath;

import java.math.BigDecimal;
import java.util.Objects;

/**
 * US19: tasa tal como la ingresó el usuario y su TEA equivalente (8 decimales, HALF_EVEN). Con TNA se aplica
 * TEA = (1 + TNA/m)^m − 1 en DECIMAL128; luego el motor deriva la TEM = (1 + TEA)^(1/12) − 1.
 */
public record RateTerms(RateType rateType, BigDecimal inputRate, Integer compoundingPeriodsPerYear, BigDecimal effectiveAnnualRate) {
    public RateTerms {
        Objects.requireNonNull(rateType, "rateType");
        Objects.requireNonNull(inputRate, "inputRate");
        Objects.requireNonNull(effectiveAnnualRate, "effectiveAnnualRate");
        if (rateType == RateType.TNA && compoundingPeriodsPerYear == null) {
            throw new IllegalArgumentException("La TNA requiere la capitalización (periodos por año).");
        }
    }

    public static RateTerms effective(BigDecimal tea) {
        return new RateTerms(RateType.TEA, tea, null, tea);
    }

    public static RateTerms nominal(BigDecimal tna, int compoundingPeriodsPerYear) {
        return new RateTerms(RateType.TNA, tna, compoundingPeriodsPerYear,
                FinancialMath.effectiveAnnualFromNominal(tna, compoundingPeriodsPerYear));
    }

    /**
     * Reconstrucción desde persistencia; las cotizaciones previas a US19 se tratan como TEA. Las columnas NUMERIC(60,24)
     * devuelven escala 24: se normaliza a la misma representación que la respuesta de cálculo (tasa ingresada sin ceros
     * a la derecha y TEA con 8 decimales), para que POST y GET expongan valores idénticos.
     */
    public static RateTerms restore(String rateType, BigDecimal inputRate, Integer periods, BigDecimal tea) {
        BigDecimal effective = canonicalRate(tea);
        if (rateType == null || inputRate == null) return new RateTerms(RateType.TEA, effective, null, effective);
        return new RateTerms(RateType.valueOf(rateType), plainRate(inputRate), periods, effective);
    }

    /** TEA con 8 decimales HALF_EVEN (misma escala que la TEA del motor y de las métricas). */
    public static BigDecimal canonicalRate(BigDecimal rate) {
        return rate == null ? null : rate.setScale(8, java.math.RoundingMode.HALF_EVEN);
    }

    /** Tasa tal como se ingresó, sin ceros a la derecha (0.120000… → 0.12). */
    public static BigDecimal plainRate(BigDecimal rate) {
        if (rate == null) return null;
        BigDecimal stripped = rate.stripTrailingZeros();
        return stripped.scale() < 0 ? stripped.setScale(0) : stripped;
    }
}
