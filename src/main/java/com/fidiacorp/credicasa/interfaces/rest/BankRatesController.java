package com.fidiacorp.credicasa.interfaces.rest;

import com.fidiacorp.credicasa.application.dto.response.BankRateResponseDto;
import com.fidiacorp.credicasa.application.usecase.GetBankRatesService;
import com.fidiacorp.credicasa.domain.model.Currency;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.util.List;

@RestController
@RequestMapping("/api/v1/banking")
@Tag(name = "Banking Integration & Benchmarks (ASR-MOD / ASR-PERF)", description = "Interoperabilidad con adaptadores bancarios (BCP, Interbank) y benchmarks SBS cacheados")
public class BankRatesController {

    private final GetBankRatesService bankRatesService;

    public BankRatesController(GetBankRatesService bankRatesService) {
        this.bankRatesService = bankRatesService;
    }

    @GetMapping("/benchmarks")
    @Operation(summary = "Obtener benchmarks de tasas del mercado SBS (Caché sub-100ms)",
            description = "Devuelve el cuadro comparativo de tasas efectivas promedio del sistema financiero peruano (SBS). " +
                    "Utiliza caché en memoria (@Cacheable) para garantizar latencia sub-100 ms en consultas recurrentes.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Lista de benchmarks obtenida con éxito")
    })
    public ResponseEntity<List<BankRateResponseDto>> getMarketBenchmarks(
            @Parameter(description = "Moneda del crédito (PEN / USD)", example = "PEN")
            @RequestParam(defaultValue = "PEN") Currency currency) {
        long startTime = System.currentTimeMillis();
        List<BankRateResponseDto> benchmarks = bankRatesService.getMarketBenchmarksDto(currency);
        long duration = System.currentTimeMillis() - startTime;
        return ResponseEntity.ok()
                .header("X-Response-Time-Ms", String.valueOf(duration))
                .body(benchmarks);
    }

    @GetMapping("/banks/{bankCode}/offer")
    @PreAuthorize("hasAnyRole('ROLE_CLIENT', 'ROLE_REALTOR')")
    @SecurityRequirement(name = "BearerAuth")
    @Operation(summary = "Consultar oferta específica de un banco vía adaptador desacoplado",
            description = "Consulta y transforma la respuesta del adaptador bancario correspondiente (BCP, Interbank) al formato canónico de CrediCasa.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Oferta bancaria obtenida"),
            @ApiResponse(responseCode = "400", description = "Banco no soportado"),
            @ApiResponse(responseCode = "401", description = "No autenticado")
    })
    public ResponseEntity<BankRateResponseDto> getBankSpecificOffer(
            @Parameter(description = "Código de banco (BCP o INTERBANK)", example = "BCP", required = true)
            @PathVariable String bankCode,
            @Parameter(description = "Moneda", example = "PEN")
            @RequestParam(defaultValue = "PEN") Currency currency,
            @Parameter(description = "Valor comercial del inmueble", example = "250000.00")
            @RequestParam(defaultValue = "250000.00") BigDecimal propertyValue,
            @Parameter(description = "Monto del crédito", example = "200000.00")
            @RequestParam(defaultValue = "200000.00") BigDecimal loanAmount,
            @Parameter(description = "Plazo en meses", example = "240")
            @RequestParam(defaultValue = "240") int termMonths) {
        BankRateResponseDto offer = bankRatesService.getBankOfferDto(bankCode, currency, propertyValue, loanAmount, termMonths);
        return ResponseEntity.ok(offer);
    }
}
