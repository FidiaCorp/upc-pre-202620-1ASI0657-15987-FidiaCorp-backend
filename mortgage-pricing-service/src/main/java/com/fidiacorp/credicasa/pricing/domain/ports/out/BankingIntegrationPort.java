package com.fidiacorp.credicasa.pricing.domain.ports.out;
import com.fidiacorp.credicasa.shared.money.Currency;

import com.fidiacorp.credicasa.pricing.domain.model.BankRateBenchmark;
import com.fidiacorp.credicasa.pricing.domain.model.MarketRates;

import java.math.BigDecimal;
import java.util.List;

public interface BankingIntegrationPort {
    /** QA-02: tasas del proveedor externo (LIVE) o, con el circuito abierto, la caché SBS (FALLBACK_CACHED). */
    MarketRates fetchMarketBenchmarks(Currency currency);
    BankRateBenchmark fetchBankQuote(String bankCode, Currency currency, BigDecimal propertyValue, BigDecimal loanAmount, int termMonths);
    boolean supportsBank(String bankCode);
}
