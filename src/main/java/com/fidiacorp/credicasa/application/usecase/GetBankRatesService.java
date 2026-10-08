package com.fidiacorp.credicasa.application.usecase;

import com.fidiacorp.credicasa.application.dto.response.BankRateResponseDto;
import com.fidiacorp.credicasa.domain.model.BankRateBenchmark;
import com.fidiacorp.credicasa.domain.model.Currency;
import com.fidiacorp.credicasa.domain.ports.in.GetBankRatesUseCase;
import com.fidiacorp.credicasa.domain.ports.out.BankingIntegrationPort;
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

    @Override
    public List<BankRateBenchmark> getMarketBenchmarks(Currency currency) {
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

    public List<BankRateResponseDto> getMarketBenchmarksDto(Currency currency) {
        return getMarketBenchmarks(currency).stream()
                .map(this::mapToDto)
                .collect(Collectors.toList());
    }

    public BankRateResponseDto getBankOfferDto(String bankCode,
                                               Currency currency,
                                               BigDecimal propertyValue,
                                               BigDecimal loanAmount,
                                               int termMonths) {
        BankRateBenchmark benchmark = getBankSpecificOffer(bankCode, currency, propertyValue, loanAmount, termMonths);
        return mapToDto(benchmark);
    }

    private BankRateResponseDto mapToDto(BankRateBenchmark b) {
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
                b.getUpdatedAt()
        );
    }
}
