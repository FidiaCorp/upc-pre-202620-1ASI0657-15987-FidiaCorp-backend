package com.fidiacorp.credicasa.domain.service;

import com.fidiacorp.credicasa.domain.model.AiPropertyRecommendation;
import com.fidiacorp.credicasa.domain.model.AiRecommendationCriteria;
import com.fidiacorp.credicasa.domain.model.Property;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

@Service
public class AiPropertyRecommendationEngine {

    private static final BigDecimal DEFAULT_ANNUAL_INTEREST_RATE = new BigDecimal("0.0950"); // 9.50% TEA
    private static final BigDecimal MAX_HEALTHY_DEBT_RATIO = new BigDecimal("0.35"); // 35% cuota/ingreso SBS

    public List<AiPropertyRecommendation> rankProperties(List<Property> properties, AiRecommendationCriteria criteria) {
        List<AiPropertyRecommendation> results = new ArrayList<>();
        int termMonths = criteria.getDesiredTermYears() * 12;

        // Tasa mensual efectiva: tem = (1 + tea)^(1/12) - 1
        double tea = DEFAULT_ANNUAL_INTEREST_RATE.doubleValue();
        double tem = Math.pow(1.0 + tea, 1.0 / 12.0) - 1.0;

        // Factor de anualidad francesa: i / (1 - (1+i)^(-n))
        double factor = tem / (1.0 - Math.pow(1.0 + tem, -termMonths));

        BigDecimal monthlyIncome = criteria.getMonthlyIncome();
        BigDecimal downPayment = criteria.getDownPaymentAvailable();
        String preferredDistrict = criteria.getPreferredDistrict().toLowerCase(Locale.ROOT);

        for (Property prop : properties) {
            // Financiamiento requerido = Precio - Cuota inicial
            BigDecimal loanRequired = prop.price().subtract(downPayment);
            if (loanRequired.compareTo(BigDecimal.ZERO) < 0) {
                loanRequired = BigDecimal.ZERO;
            }

            double estimatedInstallmentDouble = loanRequired.doubleValue() * factor;
            BigDecimal estimatedInstallment = BigDecimal.valueOf(estimatedInstallmentDouble)
                    .setScale(2, RoundingMode.HALF_UP);

            BigDecimal debtRatioPercent = monthlyIncome.compareTo(BigDecimal.ZERO) > 0
                    ? estimatedInstallment.divide(monthlyIncome, 4, RoundingMode.HALF_UP).multiply(BigDecimal.valueOf(100)).setScale(2, RoundingMode.HALF_UP)
                    : BigDecimal.valueOf(100);

            boolean isViable = debtRatioPercent.compareTo(MAX_HEALTHY_DEBT_RATIO.multiply(BigDecimal.valueOf(100))) <= 0;

            // Puntuación heurística
            int score = 0;
            StringBuilder reasons = new StringBuilder();

            // 1. Scoring Financiero (hasta 45 pts)
            if (debtRatioPercent.compareTo(BigDecimal.valueOf(25)) <= 0) {
                score += 45;
                reasons.append("Excelente viabilidad financiera: la cuota estimada representa solo el ")
                        .append(debtRatioPercent).append("% de tu ingreso mensual (dentro del rango ideal < 25%). ");
            } else if (debtRatioPercent.compareTo(BigDecimal.valueOf(35)) <= 0) {
                score += 30;
                reasons.append("Viabilidad financiera adecuada: cuota estimada dentro del límite máximo SBS del 35% (")
                        .append(debtRatioPercent).append("%). ");
            } else {
                score += 5;
                reasons.append("Alerta de sobreendeudamiento: la cuota compromete el ")
                        .append(debtRatioPercent).append("% de tus ingresos (> 35% admisible). Se recomienda mayor cuota inicial. ");
            }

            // 2. Scoring de Ubicación (hasta 30 pts)
            String addressLower = prop.address().toLowerCase(Locale.ROOT);
            if (!preferredDistrict.isBlank() && addressLower.contains(preferredDistrict)) {
                score += 30;
                reasons.append("Coincidencia exacta de ubicación con tu distrito preferido (").append(criteria.getPreferredDistrict()).append("). ");
            } else if (!preferredDistrict.isBlank()) {
                score += 10;
                reasons.append("Ubicado en zona de alta valorización metropolitana. ");
            } else {
                score += 20;
            }

            // 3. Scoring de Preferencias / Tags (hasta 25 pts)
            int tagMatches = 0;
            if (criteria.getPreferenceTags() != null) {
                for (String tag : criteria.getPreferenceTags()) {
                    String cleanTag = tag.trim().toLowerCase(Locale.ROOT);
                    if (prop.tags().stream().anyMatch(t -> t.toLowerCase(Locale.ROOT).contains(cleanTag))) {
                        tagMatches++;
                    }
                }
            }
            int tagScore = Math.min(25, tagMatches * 10);
            score += tagScore;
            if (tagMatches > 0) {
                reasons.append("Presenta ").append(tagMatches).append(" atributo(s) destacados de tu interés. ");
            }

            results.add(new AiPropertyRecommendation(
                    prop.id(),
                    prop.title(),
                    prop.address(),
                    prop.price(),
                    prop.currency(),
                    Math.min(100, score),
                    isViable,
                    estimatedInstallment,
                    debtRatioPercent,
                    reasons.toString().trim()
            ));
        }

        results.sort(Comparator.comparingInt(AiPropertyRecommendation::getRelevanceScore).reversed());
        return results;
    }
}
