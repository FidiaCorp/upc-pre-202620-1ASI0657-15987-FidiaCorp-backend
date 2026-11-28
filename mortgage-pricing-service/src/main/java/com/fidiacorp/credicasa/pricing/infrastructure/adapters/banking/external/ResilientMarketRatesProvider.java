package com.fidiacorp.credicasa.pricing.infrastructure.adapters.banking.external;

import com.fidiacorp.credicasa.pricing.domain.model.DataSource;
import com.fidiacorp.credicasa.pricing.domain.model.MarketRates;
import com.fidiacorp.credicasa.pricing.infrastructure.adapters.banking.SbsAdapter;
import com.fidiacorp.credicasa.shared.money.Currency;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.timelimiter.annotation.TimeLimiter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.LocalDate;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;

/**
 * QA-02 (ASR-AVAIL): {@code @CircuitBreaker(name = "bankingProvider")} + {@code @TimeLimiter} (1000 ms) sobre el
 * proveedor externo. Ante timeout, 5xx, conexión rechazada o circuito abierto responde con la caché SBS
 * ({@link SbsAdapter}, {@code @Cacheable("sbsRates")}) marcada {@code FALLBACK_CACHED}; las simulaciones locales nunca
 * dependen de este proveedor.
 */
@Component
public class ResilientMarketRatesProvider {
    private static final Logger log = LoggerFactory.getLogger(ResilientMarketRatesProvider.class);
    public static final String BREAKER = "bankingProvider";
    private final ExternalBankRatesClient client;
    private final SbsAdapter sbs;
    private final Executor executor;
    private final Clock clock;

    public ResilientMarketRatesProvider(ExternalBankRatesClient client, SbsAdapter sbs,
                                        @Qualifier("bankingProviderExecutor") Executor executor, Clock clock) {
        this.client = client;
        this.sbs = sbs;
        this.executor = executor;
        this.clock = clock;
    }

    @CircuitBreaker(name = BREAKER, fallbackMethod = "fallbackToSbs")
    @TimeLimiter(name = BREAKER)
    public CompletableFuture<MarketRates> fetch(Currency currency) {
        return CompletableFuture.supplyAsync(() -> {
            ExternalBankRatesClient.LiveRates live = client.fetch(currency);
            return new MarketRates(live.rates(), DataSource.LIVE, live.asOfDate());
        }, executor);
    }

    CompletableFuture<MarketRates> fallbackToSbs(Currency currency, Throwable cause) {
        log.warn("[RESILIENCE] bankingProvider degradado a FALLBACK_CACHED ({}): {}", currency, cause.toString());
        return CompletableFuture.completedFuture(new MarketRates(sbs.fetchOfficialBenchmarks(currency), DataSource.FALLBACK_CACHED,
                LocalDate.now(clock)));
    }
}
