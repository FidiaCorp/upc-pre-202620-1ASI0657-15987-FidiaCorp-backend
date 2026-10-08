package com.fidiacorp.credicasa.infrastructure.adapters.banking;

import com.fidiacorp.credicasa.domain.model.BankRateBenchmark;
import com.fidiacorp.credicasa.domain.model.Currency;
import com.fidiacorp.credicasa.domain.ports.out.BankingIntegrationPort;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.List;

/**
 * Implementación del puerto de salida BankingIntegrationPort (Arquitectura Hexagonal).
 * Orquesta la integración con SBS, BCP e Interbank, permitiendo modificar o añadir
 * bancos sin afectar las capas de dominio o aplicación.
 */
@Component
public class BankingIntegrationCompositeAdapter implements BankingIntegrationPort {

    private final SbsAdapter sbsAdapter;
    private final BcpBankingAdapter bcpAdapter;
    private final InterbankBankingAdapter interbankAdapter;

    public BankingIntegrationCompositeAdapter(SbsAdapter sbsAdapter,
                                             BcpBankingAdapter bcpAdapter,
                                             InterbankBankingAdapter interbankAdapter) {
        this.sbsAdapter = sbsAdapter;
        this.bcpAdapter = bcpAdapter;
        this.interbankAdapter = interbankAdapter;
    }

    @Override
    public List<BankRateBenchmark> fetchMarketBenchmarks(Currency currency) {
        return sbsAdapter.fetchOfficialBenchmarks(currency);
    }

    @Override
    public BankRateBenchmark fetchBankQuote(String bankCode,
                                           Currency currency,
                                           BigDecimal propertyValue,
                                           BigDecimal loanAmount,
                                           int termMonths) {
        if ("BCP".equalsIgnoreCase(bankCode)) {
            return bcpAdapter.getQuote(currency, propertyValue, loanAmount, termMonths);
        } else if ("INTERBANK".equalsIgnoreCase(bankCode)) {
            return interbankAdapter.getQuote(currency, propertyValue, loanAmount, termMonths);
        } else {
            // Consulta fallback contra el benchmark promedio oficial SBS
            return fetchMarketBenchmarks(currency).stream()
                    .filter(b -> b.getBankCode().equalsIgnoreCase(bankCode))
                    .findFirst()
                    .orElseThrow(() -> new IllegalArgumentException("Entidad bancaria no soportada: " + bankCode));
        }
    }

    @Override
    public boolean supportsBank(String bankCode) {
        return "BCP".equalsIgnoreCase(bankCode)
                || "INTERBANK".equalsIgnoreCase(bankCode)
                || "BBVA".equalsIgnoreCase(bankCode)
                || "SCOTIABANK".equalsIgnoreCase(bankCode)
                || "BANBIF".equalsIgnoreCase(bankCode);
    }
}
