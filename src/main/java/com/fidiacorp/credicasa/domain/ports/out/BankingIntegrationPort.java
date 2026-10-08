package com.fidiacorp.credicasa.domain.ports.out;

import com.fidiacorp.credicasa.domain.model.BankRateBenchmark;
import com.fidiacorp.credicasa.domain.model.Currency;

import java.math.BigDecimal;
import java.util.List;

public interface BankingIntegrationPort {
    List<BankRateBenchmark> fetchMarketBenchmarks(Currency currency);
    BankRateBenchmark fetchBankQuote(String bankCode, Currency currency, BigDecimal propertyValue, BigDecimal loanAmount, int termMonths);
    boolean supportsBank(String bankCode);
}
