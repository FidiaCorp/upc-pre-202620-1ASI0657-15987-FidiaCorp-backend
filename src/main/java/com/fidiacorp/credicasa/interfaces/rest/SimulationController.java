package com.fidiacorp.credicasa.interfaces.rest;

import com.fidiacorp.credicasa.application.dto.request.SimulationRequestDto;
import com.fidiacorp.credicasa.application.dto.response.SimulationResponseDto;
import com.fidiacorp.credicasa.application.usecase.CalculateCreditSimulationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/simulations")
@Tag(name = "Mortgage Calculations & Simulations (ASR-PERF)", description = "Motor financiero de simulación bajo el Método Francés Ordinario Vencido")
@SecurityRequirement(name = "BearerAuth")
public class SimulationController {

    private final CalculateCreditSimulationService simulationService;

    public SimulationController(CalculateCreditSimulationService simulationService) {
        this.simulationService = simulationService;
    }

    @PostMapping("/calculate")
    @PreAuthorize("hasAnyRole('ROLE_CLIENT', 'ROLE_REALTOR')")
    @Operation(summary = "Simular cronograma y métricas de crédito hipotecario",
            description = "Ejecuta el motor financiero francés (30/360), evalúa gracia total/parcial, cuotas balón, " +
                    "resuelve TIR/TCEA y VAN, garantizando saldo final 0.00 exacto.")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Simulación calculada y registrada exitosamente"),
            @ApiResponse(responseCode = "400", description = "Parámetros de entrada inválidos"),
            @ApiResponse(responseCode = "401", description = "No autenticado"),
            @ApiResponse(responseCode = "403", description = "Acceso denegado (requiere ROLE_CLIENT o ROLE_REALTOR)")
    })
    public ResponseEntity<SimulationResponseDto> calculateSimulation(@Valid @RequestBody SimulationRequestDto request) {
        SimulationResponseDto response = simulationService.simulateAndSave(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('ROLE_CLIENT', 'ROLE_REALTOR')")
    @Operation(summary = "Consultar cotización y cronograma por ID",
            description = "Obtiene el cronograma completo y métricas financieras de una simulación previamente emitida.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Cotización encontrada"),
            @ApiResponse(responseCode = "404", description = "Cotización no encontrada")
    })
    public ResponseEntity<SimulationResponseDto> getSimulationById(
            @Parameter(description = "Identificador UUID de la cotización", required = true)
            @PathVariable UUID id) {
        SimulationResponseDto response = simulationService.getById(id);
        return ResponseEntity.ok(response);
    }
}
