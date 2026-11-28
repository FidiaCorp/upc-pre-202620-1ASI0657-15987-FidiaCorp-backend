package com.fidiacorp.credicasa.pricing.application.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

@Schema(description = "US20: comparación lado a lado y mejor escenario por criterio para el deudor")
public record ScenarioComparisonResponse(List<ScenarioResultDto> scenarios, String bestByTcea, String bestByTotalInterest,
                                         String bestByMonthlyInstallment, String bestByVan, String bestByTotalCost) {
}
