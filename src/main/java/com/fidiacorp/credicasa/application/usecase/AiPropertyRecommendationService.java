package com.fidiacorp.credicasa.application.usecase;

import com.fidiacorp.credicasa.application.dto.request.AiRecommendationRequest;
import com.fidiacorp.credicasa.application.dto.response.AiRecommendationResponse;
import com.fidiacorp.credicasa.application.dto.response.PropertyRecommendationItemResponse;
import com.fidiacorp.credicasa.domain.model.AiPropertyRecommendation;
import com.fidiacorp.credicasa.domain.model.AiRecommendationCriteria;
import com.fidiacorp.credicasa.domain.model.Property;
import com.fidiacorp.credicasa.domain.model.PropertyPage;
import com.fidiacorp.credicasa.domain.model.PropertySearch;
import com.fidiacorp.credicasa.domain.ports.out.PropertyRepositoryPort;
import com.fidiacorp.credicasa.domain.service.AiPropertyRecommendationEngine;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Collections;
import java.util.List;

@Service
@Transactional(readOnly = true)
public class AiPropertyRecommendationService {

    private final PropertyRepositoryPort propertyRepository;
    private final AiPropertyRecommendationEngine engine;

    public AiPropertyRecommendationService(PropertyRepositoryPort propertyRepository,
                                          AiPropertyRecommendationEngine engine) {
        this.propertyRepository = propertyRepository;
        this.engine = engine;
    }

    public AiRecommendationResponse getRecommendations(AiRecommendationRequest request) {
        // Fetch active properties (page size up to 100)
        PropertyPage page = propertyRepository.search(new PropertySearch(
                null,
                "",
                Collections.emptySet(),
                0,
                100
        ));
        List<Property> properties = page.items();

        AiRecommendationCriteria criteria = new AiRecommendationCriteria(
                request.monthlyIncome(),
                request.downPaymentAvailable(),
                request.currency(),
                request.preferredDistrict(),
                request.desiredTermYears() != null ? request.desiredTermYears() : 20,
                request.preferenceTags()
        );

        List<AiPropertyRecommendation> ranked = engine.rankProperties(properties, criteria);

        BigDecimal maxInstallment = request.monthlyIncome().multiply(new BigDecimal("0.35"))
                .setScale(2, RoundingMode.HALF_UP);

        long viableCount = ranked.stream().filter(AiPropertyRecommendation::isFinanciallyViable).count();
        String summary = String.format(
                "Análisis completado para ingreso de %s %s. Cuota mensual máxima sugerida (35%% SBS): %s. Se identificaron %d inmuebles viables de %d analizados.",
                request.currency() != null ? request.currency().name() : "PEN",
                request.monthlyIncome().toPlainString(),
                maxInstallment.toPlainString(),
                viableCount,
                ranked.size()
        );

        List<PropertyRecommendationItemResponse> items = ranked.stream()
                .map(r -> new PropertyRecommendationItemResponse(
                        r.getPropertyId(),
                        r.getTitle(),
                        r.getAddress(),
                        r.getPrice(),
                        r.getCurrency(),
                        r.getRelevanceScore(),
                        r.isFinanciallyViable(),
                        r.getEstimatedMonthlyInstallment(),
                        r.getDebtToIncomeRatioPercent(),
                        r.getRecommendationReason()
                ))
                .toList();

        return new AiRecommendationResponse(
                request.monthlyIncome(),
                maxInstallment,
                summary,
                ranked.size(),
                items
        );
    }
}
