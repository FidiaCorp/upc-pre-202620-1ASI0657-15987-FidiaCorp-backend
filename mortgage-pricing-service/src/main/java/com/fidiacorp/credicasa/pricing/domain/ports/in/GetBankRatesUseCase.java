package com.fidiacorp.credicasa.pricing.domain.ports.in;
import com.fidiacorp.credicasa.shared.money.Currency;

import com.fidiacorp.credicasa.pricing.domain.model.BankRateBenchmark;
import com.fidiacorp.credicasa.pricing.domain.model.MarketRates;

import java.math.BigDecimal;
import java.util.List;

public interface GetBankRatesUseCase {
    MarketRates getMarketBenchmarks(Currency currency);
    BankRateBenchmark getBankSpecificOffer(String bankCode, Currency currency, BigDecimal propertyValue, BigDecimal loanAmount, int termMonths);
}
