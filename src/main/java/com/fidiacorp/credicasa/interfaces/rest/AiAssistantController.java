package com.fidiacorp.credicasa.interfaces.rest;

import com.fidiacorp.credicasa.application.dto.request.AiRecommendationRequest;
import com.fidiacorp.credicasa.application.dto.response.AiRecommendationResponse;
import com.fidiacorp.credicasa.application.usecase.AiPropertyRecommendationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@Validated
@RequestMapping("/api/v1/ai")
@Tag(name = "AI Mortgage Advisor (US15)", description = "Asistente inteligente de recomendación hipotecaria e inmobiliaria determinista")
@PreAuthorize("hasAnyRole('CLIENT','REALTOR')")
public class AiAssistantController {

    private final AiPropertyRecommendationService aiService;

    public AiAssistantController(AiPropertyRecommendationService aiService) {
        this.aiService = aiService;
    }

    @PostMapping("/recommendations")
    @Operation(summary = "Recomendar inmuebles óptimos según capacidad de pago y preferencias",
            description = "Evalúa la relación cuota/ingreso (límite 35% SBS), cuota inicial, distrito y tags para ordenar el catálogo.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Recomendaciones generadas exitosamente"),
            @ApiResponse(responseCode = "400", description = "Parámetros de búsqueda inválidos"),
            @ApiResponse(responseCode = "401", description = "No autenticado")
    })
    public ResponseEntity<AiRecommendationResponse> getRecommendations(@Valid @RequestBody AiRecommendationRequest request) {
        AiRecommendationResponse response = aiService.getRecommendations(request);
        return ResponseEntity.ok(response);
    }
}
