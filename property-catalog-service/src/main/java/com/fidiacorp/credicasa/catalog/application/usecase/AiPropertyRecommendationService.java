package com.fidiacorp.credicasa.catalog.application.usecase;

import com.fidiacorp.credicasa.catalog.application.dto.request.AiRecommendationRequest;
import com.fidiacorp.credicasa.catalog.application.dto.response.AiRecommendationResponse;
import com.fidiacorp.credicasa.catalog.application.dto.response.PropertyRecommendationItemResponse;
import com.fidiacorp.credicasa.catalog.domain.model.AiPropertyRecommendation;
import com.fidiacorp.credicasa.catalog.domain.model.AiRecommendationCriteria;
import com.fidiacorp.credicasa.catalog.domain.model.PropertySearch;
import com.fidiacorp.credicasa.catalog.domain.ports.out.PropertyRepositoryPort;
import com.fidiacorp.credicasa.catalog.domain.service.AiPropertyRecommendationEngine;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.Collections;
import java.util.List;

@Service
@Transactional(readOnly = true)
public class AiPropertyRecommendationService {

    private final PropertyRepositoryPort propertyRepository;
    private final AiPropertyRecommendationEngine engine;

    public AiPropertyRecommendationService(PropertyRepositoryPort propertyRepository, AiPropertyRecommendationEngine engine) {
        this.propertyRepository = propertyRepository;
        this.engine = engine;
    }

    public AiRecommendationResponse getRecommendations(AiRecommendationRequest request) {
        AiRecommendationCriteria criteria = new AiRecommendationCriteria(
                request.monthlyIncome(), request.downPaymentAvailable(), request.currency(), request.preferredDistrict(),
                request.desiredTermYears() != null ? request.desiredTermYears() : 20, request.preferenceTags());

        var properties = propertyRepository.search(new PropertySearch(null, "", Collections.emptySet(), 0, 100)).items();
        List<AiPropertyRecommendation> ranked = engine.rankProperties(properties, criteria);
        BigDecimal maxInstallment = engine.maxAllowableInstallment(criteria.getMonthlyIncome());
        long viableCount = ranked.stream().filter(AiPropertyRecommendation::isFinanciallyViable).count();
        String summary = String.format(
                "Análisis completado para ingreso de %s %s. Cuota mensual máxima sugerida (35%% SBS): %s. Se identificaron %d inmuebles viables de %d analizados en %s.",
                criteria.getCurrency().name(), criteria.getMonthlyIncome().toPlainString(), maxInstallment.toPlainString(),
                viableCount, ranked.size(), criteria.getCurrency().name());

        List<PropertyRecommendationItemResponse> items = ranked.stream()
                .map(r -> new PropertyRecommendationItemResponse(r.getPropertyId(), r.getTitle(), r.getAddress(), r.getPrice(),
                        r.getCurrency(), r.getRelevanceScore(), r.isFinanciallyViable(), r.getEstimatedMonthlyInstallment(),
                        r.getDebtToIncomeRatioPercent(), r.getRecommendationReason()))
                .toList();
        return new AiRecommendationResponse(criteria.getMonthlyIncome(), criteria.getCurrency(), maxInstallment, summary, ranked.size(), items);
    }
}
