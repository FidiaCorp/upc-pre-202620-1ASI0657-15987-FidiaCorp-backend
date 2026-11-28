package com.fidiacorp.credicasa.pricing.application.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

@Schema(description = "US20: página del historial de simulaciones propias (más recientes primero)")
public record SimulationPageResponse(List<SimulationSummaryResponse> items, int page, int size, long totalElements, int totalPages) {
}
