package com.fidiacorp.credicasa.infrastructure.adapters.banking;

import com.fidiacorp.credicasa.domain.model.BankRateBenchmark;
import com.fidiacorp.credicasa.domain.model.Currency;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;

/**
 * Driver 3: Modificabilidad e Interoperabilidad (ASR-MOD)
 * Adaptador concreto para Banco de Crédito del Perú (BCP).
 * Simula el consumo de la API de BCP y traduce su contrato externo propietario
 * al modelo de dominio canónico BankRateBenchmark de CrediCasa.
 */
@Component
public class BcpBankingAdapter {

    // Simulación del contrato JSON propietario expuesto por BCP
    public record BcpExternalContractResponse(
            String cod_banco,
            String razon_social,
            String cod_producto,
            String desc_producto,
            double tea_piso,
            double tea_techo,
            double tea_promedio_ponderada,
            String cod_moneda,
            int plazo_meses_max,
            double ratio_ltv_max,
            String timestamp_consulta
    ) {}

    public BankRateBenchmark getQuote(Currency currency, BigDecimal propertyValue, BigDecimal loanAmount, int termMonths) {
        BcpExternalContractResponse externalResponse = callBcpMortgageApi(currency, propertyValue, loanAmount, termMonths);
        return mapToCanonicalDomain(externalResponse, currency);
    }

    private BcpExternalContractResponse callBcpMortgageApi(Currency currency, BigDecimal propertyValue, BigDecimal loanAmount, int termMonths) {
        // Simulación de respuesta bancaria con lógica de pricing basada en LTV y plazo
        double ltv = propertyValue.compareTo(BigDecimal.ZERO) > 0
                ? loanAmount.divide(propertyValue, 4, RoundingMode.HALF_EVEN).doubleValue()
                : 0.80;

        double baseTea = currency == Currency.USD ? 0.0750 : 0.0820;
        if (ltv > 0.85) {
            baseTea += 0.0050; // Recargo por mayor LTV
        }
        if (termMonths > 240) {
            baseTea += 0.0030; // Recargo por plazo extendido
        }

        return new BcpExternalContractResponse(
                "002",
                "Banco de Crédito del Perú - BCP",
                "HIP-BCP-TRAD",
                "Crédito Hipotecario BCP Casa Única",
                baseTea - 0.0070,
                baseTea + 0.0210,
                baseTea,
                currency.name(),
                300,
                0.90,
                LocalDateTime.now().toString()
        );
    }

    private BankRateBenchmark mapToCanonicalDomain(BcpExternalContractResponse bcpDto, Currency currency) {
        return new BankRateBenchmark(
                "BCP",
                bcpDto.razon_social(),
                BigDecimal.valueOf(bcpDto.tea_piso()).setScale(4, RoundingMode.HALF_EVEN),
                BigDecimal.valueOf(bcpDto.tea_techo()).setScale(4, RoundingMode.HALF_EVEN),
                BigDecimal.valueOf(bcpDto.tea_promedio_ponderada()).setScale(4, RoundingMode.HALF_EVEN),
                currency,
                bcpDto.desc_producto(),
                bcpDto.plazo_meses_max(),
                BigDecimal.valueOf(bcpDto.ratio_ltv_max()).setScale(2, RoundingMode.HALF_EVEN),
                LocalDateTime.now()
        );
    }
}
