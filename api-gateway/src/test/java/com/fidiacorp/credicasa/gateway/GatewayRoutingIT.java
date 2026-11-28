package com.fidiacorp.credicasa.gateway;

import com.fidiacorp.credicasa.shared.security.JwtTokenIssuer;
import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.client.WireMock;
import com.github.tomakehurst.wiremock.core.WireMockConfiguration;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.reactive.server.WebTestClient;

import java.util.List;
import java.util.UUID;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathMatching;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * El gateway enruta /api/v1/** a los 5 microservicios (cada uno simulado por un WireMock propio), centraliza CORS,
 * propaga Authorization y X-Correlation-Id, bloquea la API interna, aplica revocación y rate limiting.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
        "app.security.jwt.secret=" + GatewayRoutingIT.SECRET, "app.internal.token=gateway-internal",
        "app.rate-limit.auth.requests-per-window=3", "app.security.revocation-cache-ms=0"})
class GatewayRoutingIT {
    static final String SECRET = "GatewayOnlyTestSigningKeyForIsolatedTests0123456789";
    static final String[] SERVICES = {"identity", "catalog", "pricing", "quotation", "communication"};
    static final WireMockServer[] BACKENDS = new WireMockServer[SERVICES.length];

    static {
        for (int i = 0; i < SERVICES.length; i++) {
            BACKENDS[i] = new WireMockServer(WireMockConfiguration.options().dynamicPort());
            BACKENDS[i].start();
        }
    }

    @DynamicPropertySource
    static void routes(DynamicPropertyRegistry registry) {
        for (int i = 0; i < SERVICES.length; i++) {
            WireMockServer backend = BACKENDS[i];
            registry.add("app.routes." + SERVICES[i], backend::baseUrl);
        }
    }

    @AfterAll
    static void stop() {
        for (WireMockServer backend : BACKENDS) backend.stop();
    }

    @Autowired WebTestClient http;
    String token;

    @BeforeEach
    void stubs() {
        token = new JwtTokenIssuer(SECRET, 60_000).issue(UUID.randomUUID(), List.of("ROLE_CLIENT"), "c@x.pe", "Cliente");
        for (int i = 0; i < SERVICES.length; i++) {
            BACKENDS[i].resetAll();
            // Como los microservicios reales, el backend también devuelve X-Correlation-Id: el gateway debe deduplicarlo.
            BACKENDS[i].stubFor(WireMock.any(urlPathMatching("/api/v1/.*")).atPriority(10).willReturn(aResponse()
                    .withHeader("X-Correlation-Id", "qa-corr-1")
                    .withHeader("Content-Type", "application/json").withBody("{\"service\":\"" + SERVICES[i] + "\"}")));
            BACKENDS[i].stubFor(WireMock.get(urlPathEqualTo("/v3/api-docs")).willReturn(aResponse()
                    .withHeader("Content-Type", "application/json").withBody("{\"openapi\":\"3.0.1\",\"info\":{\"title\":\"" + SERVICES[i] + "\"}}")));
        }
        BACKENDS[0].stubFor(WireMock.get(urlPathEqualTo("/api/v1/internal/tokens/status")).atPriority(1)
                .willReturn(aResponse().withHeader("Content-Type", "application/json").withBody("{\"active\":true}")));
    }

    @ParameterizedTest
    @CsvSource({"/api/v1/auth/me,identity", "/api/v1/users/me,identity", "/api/v1/subscriptions/status,identity",
            "/api/v1/payments/gateways,identity", "/api/v1/properties,catalog", "/api/v1/properties/abc,catalog",
            "/api/v1/ai/recommendations,catalog", "/api/v1/simulations,pricing", "/api/v1/simulations/compare,pricing",
            "/api/v1/banking/benchmarks,pricing", "/api/v1/offers,quotation", "/api/v1/offers/1/versions,quotation",
            "/api/v1/quotations/versions/1/verify,quotation", "/api/v1/notifications,communication",
            "/api/v1/messages/conversations,communication"})
    void routesEveryPublicPrefixToItsMicroservice(String path, String service) {
        http.get().uri(path).header(HttpHeaders.AUTHORIZATION, "Bearer " + token).header("X-Correlation-Id", "qa-corr-1")
                .exchange().expectStatus().isOk()
                .expectHeader().valueEquals("X-Correlation-Id", "qa-corr-1")
                .expectBody().jsonPath("$.service").isEqualTo(service);
        int index = List.of(SERVICES).indexOf(service);
        BACKENDS[index].verify(getRequestedFor(urlPathEqualTo(path)).withHeader("Authorization", equalTo("Bearer " + token))
                .withHeader("X-Correlation-Id", equalTo("qa-corr-1")));
    }

    @Test
    void generatesCorrelationIdsAndCentralizesCors() {
        http.get().uri("/api/v1/banking/benchmarks").exchange().expectStatus().isOk()
                .expectHeader().value("X-Correlation-Id", v -> assertThat(v).hasSize(36));
        http.get().uri("/api/v1/banking/benchmarks").header("X-Correlation-Id", "bad value!").exchange()
                .expectHeader().value("X-Correlation-Id", v -> assertThat(v).hasSize(36));
        http.options().uri("/api/v1/offers").header("Origin", "http://localhost:5173").header("Access-Control-Request-Method", "POST")
                .header("Access-Control-Request-Headers", "Authorization,Content-Type").exchange().expectStatus().isOk()
                .expectHeader().valueEquals("Access-Control-Allow-Origin", "http://localhost:5173");
        http.get().uri("/api/v1/banking/benchmarks").header("Origin", "http://127.0.0.1:4173").exchange()
                .expectHeader().valueEquals("Access-Control-Allow-Origin", "http://127.0.0.1:4173")
                .expectHeader().value("Access-Control-Expose-Headers", v -> assertThat(v).contains("X-Data-Source", "Retry-After", "Content-Disposition"));
        http.options().uri("/api/v1/offers").header("Origin", "https://evil.example").header("Access-Control-Request-Method", "POST")
                .exchange().expectStatus().isForbidden();
    }

    @Test
    void neverExposesTheInternalApi() {
        http.get().uri("/api/v1/internal/accounts/" + UUID.randomUUID()).exchange().expectStatus().isNotFound()
                .expectHeader().contentType("application/problem+json")
                .expectBody().jsonPath("$.type").isEqualTo("https://api.credicasa.pe/problems/not-found");
        http.get().uri("/api/v1/internal/tokens/status").header(HttpHeaders.AUTHORIZATION, "Bearer " + token).exchange().expectStatus().isNotFound();
        http.get().uri("/api/v1/unknown").exchange().expectStatus().isNotFound();
    }

    @Test
    void revokedTokensAreRejectedAtTheEdgeWithTheInternalToken() {
        BACKENDS[0].stubFor(WireMock.get(urlPathEqualTo("/api/v1/internal/tokens/status")).atPriority(1)
                .willReturn(aResponse().withHeader("Content-Type", "application/json").withBody("{\"active\":false}")));
        http.get().uri("/api/v1/offers").header(HttpHeaders.AUTHORIZATION, "Bearer " + token).exchange().expectStatus().isUnauthorized()
                .expectBody().jsonPath("$.status").isEqualTo(401);
        BACKENDS[0].verify(getRequestedFor(urlPathEqualTo("/api/v1/internal/tokens/status")).withHeader("X-Internal-Token", equalTo("gateway-internal")));
        BACKENDS[3].verify(0, getRequestedFor(urlPathEqualTo("/api/v1/offers")));
        http.get().uri("/api/v1/offers").header(HttpHeaders.AUTHORIZATION, "Bearer forged.token.value").exchange().expectStatus().isOk();
    }

    @Test
    void introspectionOutageFailsOpenToLocalValidation() {
        BACKENDS[0].stubFor(WireMock.get(urlPathEqualTo("/api/v1/internal/tokens/status")).atPriority(1).willReturn(aResponse().withStatus(503)));
        http.get().uri("/api/v1/properties").header(HttpHeaders.AUTHORIZATION, "Bearer " + token).exchange().expectStatus().isOk();
    }

    @Test
    void rateLimitsCredentialEndpoints() {
        for (int i = 0; i < 3; i++) http.post().uri("/api/v1/auth/register").bodyValue("{}").header("Content-Type", "application/json").exchange().expectStatus().isOk();
        http.post().uri("/api/v1/auth/register").bodyValue("{}").header("Content-Type", "application/json").exchange()
                .expectStatus().isEqualTo(429).expectHeader().exists("Retry-After");
        http.get().uri("/api/v1/auth/me").exchange().expectStatus().isOk();
    }

    @Test
    void aggregatesTheMicroservicesOpenApiAndExposesHealth() {
        for (String service : SERVICES) {
            http.get().uri("/docs/" + service + "/v3/api-docs").exchange().expectStatus().isOk().expectBody().jsonPath("$.info.title").isEqualTo(service);
        }
        http.get().uri("/v3/api-docs/swagger-config").exchange().expectStatus().isOk()
                .expectBody().jsonPath("$.urls.length()").isEqualTo(5);
        http.get().uri("/actuator/health").exchange().expectStatus().isOk().expectBody().jsonPath("$.status").isEqualTo("UP");
    }
}
