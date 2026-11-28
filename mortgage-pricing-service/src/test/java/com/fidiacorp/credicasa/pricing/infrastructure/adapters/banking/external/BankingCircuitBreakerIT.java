package com.fidiacorp.credicasa.pricing.infrastructure.adapters.banking.external;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.core.WireMockConfiguration;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * QA-02 (ASR-AVAIL) con WireMock real en proceso (equivalente a external-bank-mock :8090): LIVE → 503/timeout →
 * CLOSED→OPEN con respuestas FALLBACK_CACHED rápidas → HALF_OPEN → CLOSED al recuperarse el proveedor.
 */
@SpringBootTest(properties = {"spring.datasource.url=jdbc:h2:mem:pricing_cb;MODE=PostgreSQL;DB_CLOSE_DELAY=-1",
        "app.banking.external.enabled=true"})
@AutoConfigureMockMvc
class BankingCircuitBreakerIT {
    static final WireMockServer bank = new WireMockServer(WireMockConfiguration.options().dynamicPort());
    static final String LIVE_BODY = """
            {"provider":"external-bank-mock","asOfDate":"2026-11-20","currency":"PEN","rates":[
              {"bankCode":"BCP","bankName":"BCP (LIVE)","minTea":0.0780,"maxTea":0.1040,"averageTea":0.0871,"productName":"Hipotecario","maxTermMonths":300,"maxLtv":0.90},
              {"bankCode":"INTERBANK","bankName":"Interbank (LIVE)","minTea":0.0770,"maxTea":0.1030,"averageTea":0.0860,"productName":"Hipotecario","maxTermMonths":300,"maxLtv":0.90}]}
            """;

    static {
        bank.start();
    }

    @DynamicPropertySource
    static void bankUrl(DynamicPropertyRegistry registry) {
        registry.add("app.banking.external.base-url", bank::baseUrl);
    }

    @AfterAll
    static void stop() {
        bank.stop();
    }

    @Autowired MockMvc mvc;
    @Autowired CircuitBreakerRegistry registry;
    CircuitBreaker breaker;

    @BeforeEach
    void reset() {
        bank.resetAll();
        breaker = registry.circuitBreaker(ResilientMarketRatesProvider.BREAKER);
        breaker.reset();
    }

    private void stubLive() {
        bank.stubFor(com.github.tomakehurst.wiremock.client.WireMock.get(urlPathEqualTo("/bank-rates/benchmarks")).willReturn(aResponse().withHeader("Content-Type", "application/json")
                .withBody(LIVE_BODY)));
    }

    @Test
    @DisplayName("QA-02: proveedor sano → LIVE con asOfDate del proveedor")
    void liveProvider() throws Exception {
        stubLive();
        mvc.perform(get("/api/v1/banking/benchmarks").param("currency", "PEN"))
                .andExpect(status().isOk()).andExpect(header().string("X-Data-Source", "LIVE"))
                .andExpect(jsonPath("$[0].bankName").value("BCP (LIVE)")).andExpect(jsonPath("$[0].fallbackActive").value(false))
                .andExpect(jsonPath("$[0].dataSource").value("LIVE")).andExpect(jsonPath("$[0].asOfDate").value("2026-11-20"));
        assertThat(breaker.getState()).isEqualTo(CircuitBreaker.State.CLOSED);
    }

    @Test
    @DisplayName("QA-02: 503 repetidos abren el circuito y la caché SBS responde FALLBACK_CACHED; luego HALF_OPEN → CLOSED")
    void outageOpensAndRecovers() throws Exception {
        bank.stubFor(com.github.tomakehurst.wiremock.client.WireMock.get(urlPathEqualTo("/bank-rates/benchmarks")).willReturn(aResponse().withStatus(503)));
        for (int i = 0; i < 10; i++) {
            mvc.perform(get("/api/v1/banking/benchmarks")).andExpect(status().isOk())
                    .andExpect(header().string("X-Data-Source", "FALLBACK_CACHED")).andExpect(jsonPath("$[0].fallbackActive").value(true))
                    .andExpect(jsonPath("$.length()").value(5));
        }
        assertThat(breaker.getState()).isEqualTo(CircuitBreaker.State.OPEN);
        int callsWhileOpen = bank.getAllServeEvents().size();
        long start = System.nanoTime();
        mvc.perform(get("/api/v1/banking/benchmarks")).andExpect(header().string("X-Data-Source", "FALLBACK_CACHED"));
        assertThat((System.nanoTime() - start) / 1_000_000).as("fallback rápido con circuito abierto").isLessThan(500);
        assertThat(bank.getAllServeEvents()).hasSize(callsWhileOpen);
        mvc.perform(get("/actuator/circuitbreakers")).andExpect(status().isOk())
                .andExpect(jsonPath("$.circuitBreakers.bankingProvider.state").value("OPEN"));

        bank.resetAll();
        stubLive();
        breaker.transitionToHalfOpenState();
        for (int i = 0; i < 3; i++) {
            mvc.perform(get("/api/v1/banking/benchmarks")).andExpect(header().string("X-Data-Source", "LIVE"));
        }
        assertThat(breaker.getState()).isEqualTo(CircuitBreaker.State.CLOSED);
    }

    @Test
    @DisplayName("QA-02: un proveedor lento (2 s) es cortado por el TimeLimiter de 1000 ms")
    void slowProviderTimesOut() throws Exception {
        bank.stubFor(com.github.tomakehurst.wiremock.client.WireMock.get(urlPathEqualTo("/bank-rates/benchmarks")).willReturn(aResponse().withFixedDelay(2500)
                .withHeader("Content-Type", "application/json").withBody(LIVE_BODY)));
        long start = System.nanoTime();
        mvc.perform(get("/api/v1/banking/benchmarks")).andExpect(header().string("X-Data-Source", "FALLBACK_CACHED"));
        long elapsed = (System.nanoTime() - start) / 1_000_000;
        assertThat(elapsed).isBetween(900L, 2200L);
        assertThat(breaker.getMetrics().getNumberOfFailedCalls()).isEqualTo(1);
    }

    @Test
    @DisplayName("QA-02: respuesta vacía del proveedor también degrada a la caché")
    void emptyResponseFallsBack() throws Exception {
        bank.stubFor(com.github.tomakehurst.wiremock.client.WireMock.get(urlPathEqualTo("/bank-rates/benchmarks")).willReturn(aResponse().withHeader("Content-Type", "application/json")
                .withBody("{\"rates\":[]}")));
        mvc.perform(get("/api/v1/banking/benchmarks").param("currency", "USD")).andExpect(header().string("X-Data-Source", "FALLBACK_CACHED"))
                .andExpect(jsonPath("$[0].currency").value("USD"));
    }
}
