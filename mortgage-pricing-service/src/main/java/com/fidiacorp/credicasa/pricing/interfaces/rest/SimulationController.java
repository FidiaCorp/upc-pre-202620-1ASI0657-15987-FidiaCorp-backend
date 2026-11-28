package com.fidiacorp.credicasa.pricing.interfaces.rest;

import com.fidiacorp.credicasa.pricing.application.dto.request.SimulationRequestDto;
import com.fidiacorp.credicasa.pricing.application.dto.response.SimulationResponseDto;
import com.fidiacorp.credicasa.pricing.application.usecase.CalculateCreditSimulationService;
import com.fidiacorp.credicasa.pricing.application.dto.response.ScheduleExportFile;
import com.fidiacorp.credicasa.pricing.application.usecase.ExportScheduleService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.security.Principal;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/simulations")
@Tag(name = "Mortgage Calculations & Simulations (ASR-PERF)", description = "Motor financiero de simulación bajo el Método Francés Ordinario Vencido")
@SecurityRequirement(name = "BearerAuth")
public class SimulationController {

    private final CalculateCreditSimulationService simulationService;
    private final ExportScheduleService exportService;

    public SimulationController(CalculateCreditSimulationService simulationService,
                                ExportScheduleService exportService) {
        this.simulationService = simulationService;
        this.exportService = exportService;
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
    public ResponseEntity<SimulationResponseDto> calculateSimulation(Principal principal, @Valid @RequestBody SimulationRequestDto request) {
        SimulationResponseDto response = simulationService.simulateAndSave(requester(principal), request);
        return ResponseEntity.status(HttpStatus.CREATED).cacheControl(org.springframework.http.CacheControl.noStore()).body(response);
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('ROLE_CLIENT', 'ROLE_REALTOR')")
    @Operation(summary = "Consultar cotización y cronograma por ID",
            description = "Obtiene el cronograma completo y métricas financieras de una simulación previamente emitida.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Cotización encontrada"),
            @ApiResponse(responseCode = "404", description = "Cotización no encontrada")
    })
    public ResponseEntity<SimulationResponseDto> getSimulationById(Principal principal,
            @Parameter(description = "Identificador UUID de la cotización", required = true)
            @PathVariable UUID id) {
        SimulationResponseDto response = simulationService.getById(UUID.fromString(principal.getName()), id);
        return ResponseEntity.ok().cacheControl(org.springframework.http.CacheControl.noStore()).body(response);
    }

    @GetMapping("/{id}/export/xlsx")
    @PreAuthorize("hasAnyRole('ROLE_CLIENT', 'ROLE_REALTOR')")
    @Operation(summary = "Descargar cronograma de pagos en formato Excel (.xlsx)",
            description = "Genera y descarga una hoja de cálculo con las métricas financieras y el cronograma completo cuota a cuota.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Archivo Excel generado exitosamente"),
            @ApiResponse(responseCode = "401", description = "No autenticado"),
            @ApiResponse(responseCode = "404", description = "Cotización no encontrada")
    })
    public ResponseEntity<byte[]> exportSimulationExcel(Principal principal, @PathVariable UUID id) {
        return download(exportService.exportSimulation(UUID.fromString(principal.getName()), id));
    }

    @GetMapping
    @PreAuthorize("hasAnyRole('ROLE_CLIENT', 'ROLE_REALTOR')")
    @io.swagger.v3.oas.annotations.Operation(summary = "US20: listar mis simulaciones",
            description = "Historial paginado (más recientes primero) de las simulaciones del usuario autenticado, con tipo de tasa, TEA, TCEA e intereses.")
    public ResponseEntity<com.fidiacorp.credicasa.pricing.application.dto.response.SimulationPageResponse> listSimulations(Principal principal,
            @org.springframework.web.bind.annotation.RequestParam(defaultValue = "0") int page,
            @org.springframework.web.bind.annotation.RequestParam(defaultValue = "10") int size) {
        return ResponseEntity.ok().cacheControl(org.springframework.http.CacheControl.noStore())
                .body(simulationService.listOwn(UUID.fromString(principal.getName()), page, size));
    }

    @PostMapping("/compare")
    @PreAuthorize("hasAnyRole('ROLE_CLIENT', 'ROLE_REALTOR')")
    @io.swagger.v3.oas.annotations.Operation(summary = "US20: comparar 2–3 escenarios lado a lado",
            description = "Calcula sin persistir 2 a 3 escenarios (TEA o TNA, plazos, gracia, prepago opcional REDUCE_TERM/REDUCE_INSTALLMENT) y "
                    + "devuelve TCEA, VAN, TIR mensual, cuota y total de intereses con el mejor escenario por criterio.")
    public ResponseEntity<com.fidiacorp.credicasa.pricing.application.dto.response.ScenarioComparisonResponse> compareScenarios(Principal principal,
            @Valid @RequestBody com.fidiacorp.credicasa.pricing.application.dto.request.CompareScenariosRequest request) {
        return ResponseEntity.ok().cacheControl(org.springframework.http.CacheControl.noStore())
                .body(simulationService.compare(requester(principal), request));
    }

    private static CalculateCreditSimulationService.Requester requester(Principal principal) {
        return new CalculateCreditSimulationService.Requester(com.fidiacorp.credicasa.shared.security.AuthenticatedUser.id(principal),
                com.fidiacorp.credicasa.shared.security.AuthenticatedUser.displayName(principal),
                com.fidiacorp.credicasa.shared.security.AuthenticatedUser.email(principal).orElse(null));
    }

    static ResponseEntity<byte[]> download(ScheduleExportFile file) {
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + file.filename() + "\"")
                .cacheControl(org.springframework.http.CacheControl.noStore())
                .contentType(MediaType.parseMediaType(file.mediaType()))
                .body(file.content());
    }
}
