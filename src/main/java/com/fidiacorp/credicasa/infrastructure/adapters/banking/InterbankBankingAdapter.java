package com.fidiacorp.credicasa.infrastructure.adapters.banking;

import com.fidiacorp.credicasa.domain.model.BankRateBenchmark;
import com.fidiacorp.credicasa.domain.model.Currency;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;

/**
 * Driver 3: Modificabilidad e Interoperabilidad (ASR-MOD)
 * Adaptador concreto para Interbank (Banco Internacional del Perú).
 * Transforma el contrato externo de Interbank al modelo canónico de CrediCasa.
 */
@Component
public class InterbankBankingAdapter {

    // Simulación del contrato propietario de API de Interbank
    public record InterbankExternalContractResponse(
            String financialEntityCode,
            String commercialName,
            String mortgageProgram,
            double nominalRateMin,
            double nominalRateMax,
            double effectiveAnnualRateAvg,
            String currencyIso,
            int maxTenorMonths,
            double maximumLtvAllowed,
            long responseTimestampEpoch
    ) {}

    public BankRateBenchmark getQuote(Currency currency, BigDecimal propertyValue, BigDecimal loanAmount, int termMonths) {
        InterbankExternalContractResponse response = callInterbankApi(currency, propertyValue, loanAmount, termMonths);
        return mapToCanonicalDomain(response, currency);
    }

    private InterbankExternalContractResponse callInterbankApi(Currency currency, BigDecimal propertyValue, BigDecimal loanAmount, int termMonths) {
        double ltv = propertyValue.compareTo(BigDecimal.ZERO) > 0
                ? loanAmount.divide(propertyValue, 4, RoundingMode.HALF_EVEN).doubleValue()
                : 0.80;

        double baseTea = currency == Currency.USD ? 0.0740 : 0.0810;
        if (ltv > 0.80) {
            baseTea += 0.0040;
        }

        return new InterbankExternalContractResponse(
                "IBK",
                "Interbank - Banco Internacional del Perú",
                "Crédito Hipotecario Mi Propiedad Interbank",
                baseTea - 0.0060,
                baseTea + 0.0240,
                baseTea,
                currency.name(),
                360,
                0.90,
                System.currentTimeMillis()
        );
    }

    private BankRateBenchmark mapToCanonicalDomain(InterbankExternalContractResponse ibkDto, Currency currency) {
        return new BankRateBenchmark(
                "INTERBANK",
                ibkDto.commercialName(),
                BigDecimal.valueOf(ibkDto.nominalRateMin()).setScale(4, RoundingMode.HALF_EVEN),
                BigDecimal.valueOf(ibkDto.nominalRateMax()).setScale(4, RoundingMode.HALF_EVEN),
                BigDecimal.valueOf(ibkDto.effectiveAnnualRateAvg()).setScale(4, RoundingMode.HALF_EVEN),
                currency,
                ibkDto.mortgageProgram(),
                ibkDto.maxTenorMonths(),
                BigDecimal.valueOf(ibkDto.maximumLtvAllowed()).setScale(2, RoundingMode.HALF_EVEN),
                LocalDateTime.now()
        );
    }
}
