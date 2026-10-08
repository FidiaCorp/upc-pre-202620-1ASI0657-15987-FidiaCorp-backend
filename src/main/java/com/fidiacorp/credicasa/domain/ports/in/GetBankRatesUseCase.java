package com.fidiacorp.credicasa.domain.ports.in;

import com.fidiacorp.credicasa.domain.model.BankRateBenchmark;
import com.fidiacorp.credicasa.domain.model.Currency;

import java.math.BigDecimal;
import java.util.List;

public interface GetBankRatesUseCase {
    List<BankRateBenchmark> getMarketBenchmarks(Currency currency);
    BankRateBenchmark getBankSpecificOffer(String bankCode, Currency currency, BigDecimal propertyValue, BigDecimal loanAmount, int termMonths);
}
