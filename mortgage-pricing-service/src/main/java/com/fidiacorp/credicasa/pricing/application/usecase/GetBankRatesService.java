package com.fidiacorp.credicasa.pricing.application.usecase;
import com.fidiacorp.credicasa.shared.money.Currency;

import com.fidiacorp.credicasa.pricing.application.dto.response.BankRateResponseDto;
import com.fidiacorp.credicasa.pricing.domain.model.BankRateBenchmark;
import com.fidiacorp.credicasa.pricing.domain.ports.in.GetBankRatesUseCase;
import com.fidiacorp.credicasa.pricing.domain.ports.out.BankingIntegrationPort;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.List;
import java.util.stream.Collectors;

@Service
public class GetBankRatesService implements GetBankRatesUseCase {

    private final BankingIntegrationPort bankingIntegrationPort;

    public GetBankRatesService(BankingIntegrationPort bankingIntegrationPort) {
        this.bankingIntegrationPort = bankingIntegrationPort;
    }

    /** Lista para el controlador junto con el origen (cabecera X-Data-Source). */
    public record MarketRatesView(List<BankRateResponseDto> rates, String dataSource) {}

    @Override
    public com.fidiacorp.credicasa.pricing.domain.model.MarketRates getMarketBenchmarks(Currency currency) {
        return bankingIntegrationPort.fetchMarketBenchmarks(currency);
    }

    @Override
    public BankRateBenchmark getBankSpecificOffer(String bankCode,
                                                  Currency currency,
                                                  BigDecimal propertyValue,
                                                  BigDecimal loanAmount,
                                                  int termMonths) {
        return bankingIntegrationPort.fetchBankQuote(bankCode, currency, propertyValue, loanAmount, termMonths);
    }

    public MarketRatesView getMarketBenchmarksDto(Currency currency) {
        var market = getMarketBenchmarks(currency);
        return new MarketRatesView(market.rates().stream()
                .map(b -> mapToDto(b, market.source().name(), market.fallbackActive(), market.asOfDate()))
                .collect(Collectors.toList()), market.source().name());
    }

    public BankRateResponseDto getBankOfferDto(String bankCode,
                                               Currency currency,
                                               BigDecimal propertyValue,
                                               BigDecimal loanAmount,
                                               int termMonths) {
        BankRateBenchmark benchmark = getBankSpecificOffer(bankCode, currency, propertyValue, loanAmount, termMonths);
        return mapToDto(benchmark, "LIVE", false, benchmark.getUpdatedAt().toLocalDate());
    }

    private BankRateResponseDto mapToDto(BankRateBenchmark b, String source, boolean fallback, java.time.LocalDate asOf) {
        return new BankRateResponseDto(
                b.getBankCode(),
                b.getBankName(),
                b.getMinTea(),
                b.getMaxTea(),
                b.getAverageTea(),
                b.getCurrency(),
                b.getProductName(),
                b.getMaxTermMonths(),
                b.getMaxLtv(),
                b.getUpdatedAt(),
                source,
                fallback,
                asOf
        );
    }
}
