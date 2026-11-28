package com.fidiacorp.credicasa.pricing.domain.model;

import java.util.List;

/** US20: comparación lado a lado y el mejor escenario por criterio (desde la perspectiva del deudor). */
public record ScenarioComparison(List<ScenarioOutcome> scenarios, String bestByTcea, String bestByTotalInterest,
                                 String bestByMonthlyInstallment, String bestByVan, String bestByTotalCost) {
}
