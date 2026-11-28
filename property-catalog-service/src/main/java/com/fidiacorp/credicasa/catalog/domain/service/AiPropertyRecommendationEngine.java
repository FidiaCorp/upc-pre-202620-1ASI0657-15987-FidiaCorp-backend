package com.fidiacorp.credicasa.catalog.domain.service;

import com.fidiacorp.credicasa.catalog.domain.model.AiPropertyRecommendation;
import com.fidiacorp.credicasa.catalog.domain.model.AiRecommendationCriteria;
import com.fidiacorp.credicasa.catalog.domain.model.Property;
import com.fidiacorp.credicasa.shared.finance.FinancialMath;

import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

/**
 * Motor determinista del asesor hipotecario (US15). Dominio puro: sin anotaciones de Spring, sin {@code double}
 * ni {@code Math.pow}. La cuota se calcula con la matemática financiera del shared kernel ({@link FinancialMath}:
 * DECIMAL128, HALF_EVEN; la misma que usa mortgage-pricing-service) y solo se comparan inmuebles en la misma moneda del
 * criterio (no se mezclan PEN y USD).
 */
public class AiPropertyRecommendationEngine {

    public static final BigDecimal DEFAULT_ANNUAL_INTEREST_RATE = new BigDecimal("0.0950"); // 9.50% TEA
    public static final BigDecimal MAX_HEALTHY_DEBT_RATIO = new BigDecimal("0.35");         // 35% cuota/ingreso SBS
    private static final MathContext MC = MathContext.DECIMAL128;
    private static final RoundingMode ROUND = RoundingMode.HALF_EVEN;
    private static final BigDecimal HUNDRED = BigDecimal.valueOf(100);
    private static final BigDecimal IDEAL_RATIO_PERCENT = BigDecimal.valueOf(25);

    private final BigDecimal annualRate;

    public AiPropertyRecommendationEngine() {
        this(DEFAULT_ANNUAL_INTEREST_RATE);
    }

    public AiPropertyRecommendationEngine(BigDecimal annualRate) {
        this.annualRate = annualRate == null ? DEFAULT_ANNUAL_INTEREST_RATE : annualRate;
    }

    public BigDecimal maxAllowableInstallment(BigDecimal monthlyIncome) {
        return monthlyIncome.multiply(MAX_HEALTHY_DEBT_RATIO, MC).setScale(2, ROUND);
    }

    public List<AiPropertyRecommendation> rankProperties(List<Property> properties, AiRecommendationCriteria criteria) {
        int termMonths = criteria.getDesiredTermYears() * 12;
        BigDecimal tem = FinancialMath.monthlyRateFromAnnualEffective(annualRate);
        BigDecimal monthlyIncome = criteria.getMonthlyIncome();
        BigDecimal downPayment = criteria.getDownPaymentAvailable();
        String preferredDistrict = criteria.getPreferredDistrict();
        BigDecimal maxRatioPercent = MAX_HEALTHY_DEBT_RATIO.multiply(HUNDRED);

        List<AiPropertyRecommendation> results = new ArrayList<>();
        for (Property prop : properties) {
            if (prop.currency() != criteria.getCurrency()) continue; // nunca mezclar PEN y USD

            BigDecimal loanRequired = prop.price().subtract(downPayment).max(BigDecimal.ZERO);
            BigDecimal estimatedInstallment = FinancialMath.levelInstallment(loanRequired, tem, termMonths);
            BigDecimal debtRatioPercent = estimatedInstallment.multiply(HUNDRED, MC).divide(monthlyIncome, MC).setScale(2, ROUND);
            boolean viable = debtRatioPercent.compareTo(maxRatioPercent) <= 0;

            int score;
            StringBuilder reasons = new StringBuilder();
            if (debtRatioPercent.compareTo(IDEAL_RATIO_PERCENT) <= 0) {
                score = 45;
                reasons.append("Excelente viabilidad financiera: la cuota estimada representa solo el ")
                        .append(debtRatioPercent.toPlainString()).append("% de tu ingreso mensual (dentro del rango ideal < 25%). ");
            } else if (viable) {
                score = 30;
                reasons.append("Viabilidad financiera adecuada: cuota estimada dentro del límite máximo SBS del 35% (")
                        .append(debtRatioPercent.toPlainString()).append("%). ");
            } else {
                score = 5;
                reasons.append("Alerta de sobreendeudamiento: la cuota compromete el ")
                        .append(debtRatioPercent.toPlainString()).append("% de tus ingresos (> 35% admisible). Se recomienda mayor cuota inicial. ");
            }

            String addressLower = prop.address().toLowerCase(Locale.ROOT);
            if (preferredDistrict.isBlank()) {
                score += 20;
            } else if (addressLower.contains(preferredDistrict)) {
                score += 30;
                reasons.append("Coincidencia exacta de ubicación con tu distrito preferido (").append(preferredDistrict).append("). ");
            } else {
                score += 10;
                reasons.append("Ubicado fuera de tu distrito preferido, en zona metropolitana. ");
            }

            int tagMatches = 0;
            for (String tag : criteria.getPreferenceTags()) {
                if (tag == null || tag.isBlank()) continue;
                String cleanTag = tag.trim().toLowerCase(Locale.ROOT);
                if (prop.tags().stream().anyMatch(t -> t.toLowerCase(Locale.ROOT).contains(cleanTag))) tagMatches++;
            }
            score += Math.min(25, tagMatches * 10);
            if (tagMatches > 0) reasons.append("Presenta ").append(tagMatches).append(" atributo(s) destacados de tu interés. ");

            results.add(new AiPropertyRecommendation(prop.id(), prop.title(), prop.address(), prop.price(), prop.currency(),
                    Math.min(100, score), viable, estimatedInstallment, debtRatioPercent, reasons.toString().trim()));
        }
        results.sort(Comparator.comparingInt(AiPropertyRecommendation::getRelevanceScore).reversed()
                .thenComparing(AiPropertyRecommendation::getEstimatedMonthlyInstallment));
        return results;
    }
}
