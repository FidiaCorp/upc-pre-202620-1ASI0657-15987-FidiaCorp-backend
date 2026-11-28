package com.fidiacorp.credicasa.pricing.infrastructure.adapters.banking;
import com.fidiacorp.credicasa.shared.money.Currency;

import com.fidiacorp.credicasa.pricing.domain.model.BankRateBenchmark;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** TC-INT-02: traducción de contratos externos simulados (BCP, Interbank, SBS) al modelo canónico. */
class BankingAdaptersTest {
    private final SbsAdapter sbs = new SbsAdapter();
    private final BcpBankingAdapter bcp = new BcpBankingAdapter();
    private final InterbankBankingAdapter interbank = new InterbankBankingAdapter();
    // Proveedor externo deshabilitado: sin circuito (no hay proxy AOP) el futuro falla y el compuesto usa solo los adaptadores locales.
    private final com.fidiacorp.credicasa.pricing.infrastructure.adapters.banking.external.ResilientMarketRatesProvider provider =
            new com.fidiacorp.credicasa.pricing.infrastructure.adapters.banking.external.ResilientMarketRatesProvider(
                    new com.fidiacorp.credicasa.pricing.infrastructure.adapters.banking.external.ExternalBankRatesClient("http://127.0.0.1:9", false, 100, 100),
                    sbs, Runnable::run, java.time.Clock.systemUTC());
    private final BankingIntegrationCompositeAdapter composite = new BankingIntegrationCompositeAdapter(sbs, bcp, interbank, provider);

    @ParameterizedTest(name = "BCP {0} LTV {1}/{2} plazo {3} → TEA {4}")
    @CsvSource({
            "PEN, 200000, 150000, 240, 0.0820",
            "PEN, 200000, 180000, 240, 0.0870",
            "PEN, 200000, 150000, 300, 0.0850",
            "USD, 200000, 190000, 360, 0.0830",
            "USD, 0, 100000, 120, 0.0750"
    })
    void bcpPricingDependsOnCurrencyLtvAndTerm(Currency currency, BigDecimal property, BigDecimal loan, int term, BigDecimal expectedTea) {
        BankRateBenchmark quote = bcp.getQuote(currency, property, loan, term);
        assertThat(quote.getBankCode()).isEqualTo("BCP");
        assertThat(quote.getCurrency()).isEqualTo(currency);
        assertThat(quote.getAverageTea()).isEqualByComparingTo(expectedTea);
        assertThat(quote.getMinTea()).isEqualByComparingTo(expectedTea.subtract(new BigDecimal("0.0070")));
        assertThat(quote.getMaxTea()).isEqualByComparingTo(expectedTea.add(new BigDecimal("0.0210")));
        assertThat(quote.getMaxTermMonths()).isEqualTo(300);
        assertThat(quote.getMaxLtv()).isEqualByComparingTo("0.90");
        assertThat(quote.getProductName()).contains("BCP");
        assertThat(quote.getBankName()).contains("Banco de Crédito");
        assertThat(quote.getUpdatedAt()).isNotNull();
    }

    @ParameterizedTest(name = "Interbank {0} LTV {1}/{2} → TEA {3}")
    @CsvSource({
            "PEN, 200000, 150000, 0.0810",
            "PEN, 200000, 170000, 0.0850",
            "USD, 200000, 100000, 0.0740",
            "USD, 0, 100000, 0.0740"
    })
    void interbankPricingDependsOnCurrencyAndLtv(Currency currency, BigDecimal property, BigDecimal loan, BigDecimal expectedTea) {
        BankRateBenchmark quote = interbank.getQuote(currency, property, loan, 240);
        assertThat(quote.getBankCode()).isEqualTo("INTERBANK");
        assertThat(quote.getAverageTea()).isEqualByComparingTo(expectedTea);
        assertThat(quote.getMinTea()).isEqualByComparingTo(expectedTea.subtract(new BigDecimal("0.0060")));
        assertThat(quote.getMaxTea()).isEqualByComparingTo(expectedTea.add(new BigDecimal("0.0240")));
        assertThat(quote.getMaxTermMonths()).isEqualTo(360);
        assertThat(quote.getProductName()).contains("Interbank");
    }

    @Test
    @DisplayName("SBS: benchmarks de 5 entidades con la tasa promedio de la moneda solicitada")
    void sbsBenchmarksUseCurrencySpecificAverages() {
        List<BankRateBenchmark> pen = sbs.fetchOfficialBenchmarks(Currency.PEN);
        List<BankRateBenchmark> usd = sbs.fetchOfficialBenchmarks(Currency.USD);
        assertThat(pen).extracting(BankRateBenchmark::getBankCode).containsExactly("BCP", "INTERBANK", "BBVA", "SCOTIABANK", "BANBIF");
        assertThat(pen.get(0).getAverageTea()).isEqualByComparingTo("0.0885");
        assertThat(usd.get(0).getAverageTea()).isEqualByComparingTo("0.0760");
        assertThat(usd).allSatisfy(b -> {
            assertThat(b.getCurrency()).isEqualTo(Currency.USD);
            assertThat(b.getMinTea()).isLessThan(b.getMaxTea());
            assertThat(b.getMaxTermMonths()).isEqualTo(300);
        });
    }

    @Test
    @DisplayName("SBS: una interrupción del hilo durante la consulta conserva la bandera de interrupción")
    void sbsPreservesInterruptFlag() {
        Thread.currentThread().interrupt();
        try {
            assertThat(sbs.fetchOfficialBenchmarks(Currency.PEN)).hasSize(5);
            assertThat(Thread.currentThread().isInterrupted()).isTrue();
        } finally {
            Thread.interrupted();
        }
    }

    @Test
    @DisplayName("Composite: enruta BCP/INTERBANK a su adaptador y el resto al benchmark SBS; rechaza bancos desconocidos")
    void compositeRoutesQuotesAndSupportsKnownBanks() {
        BigDecimal value = new BigDecimal("300000"), loan = new BigDecimal("240000");
        assertThat(composite.fetchBankQuote("bcp", Currency.PEN, value, loan, 240).getBankCode()).isEqualTo("BCP");
        assertThat(composite.fetchBankQuote("Interbank", Currency.PEN, value, loan, 240).getBankCode()).isEqualTo("INTERBANK");
        BankRateBenchmark bbva = composite.fetchBankQuote("bbva", Currency.USD, value, loan, 240);
        assertThat(bbva.getBankCode()).isEqualTo("BBVA");
        assertThat(bbva.getAverageTea()).isEqualByComparingTo("0.0770");
        assertThat(sbs.fetchOfficialBenchmarks(Currency.PEN)).hasSize(5);
        assertThatThrownBy(() -> composite.fetchBankQuote("PICHINCHA", Currency.PEN, value, loan, 240))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("PICHINCHA");
        for (String bank : List.of("BCP", "interbank", "BBVA", "Scotiabank", "BANBIF")) assertThat(composite.supportsBank(bank)).as(bank).isTrue();
        assertThat(composite.supportsBank("PICHINCHA")).isFalse();
    }
}
