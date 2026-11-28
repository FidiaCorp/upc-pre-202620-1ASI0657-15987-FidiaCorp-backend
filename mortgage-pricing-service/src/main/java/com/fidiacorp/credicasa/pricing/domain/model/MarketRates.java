package com.fidiacorp.credicasa.pricing.domain.model;

import java.time.LocalDate;
import java.util.List;
import java.util.Objects;

/** Referencias de tasas con su procedencia (LIVE | FALLBACK_CACHED) y fecha de corte. */
public record MarketRates(List<BankRateBenchmark> rates, DataSource source, LocalDate asOfDate) {
    public MarketRates {
        rates = List.copyOf(Objects.requireNonNull(rates, "rates"));
        Objects.requireNonNull(source, "source");
        Objects.requireNonNull(asOfDate, "asOfDate");
    }

    public boolean fallbackActive() {
        return source == DataSource.FALLBACK_CACHED;
    }
}
