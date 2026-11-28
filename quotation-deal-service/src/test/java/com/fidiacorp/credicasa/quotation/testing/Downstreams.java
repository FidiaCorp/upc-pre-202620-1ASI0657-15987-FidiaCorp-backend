package com.fidiacorp.credicasa.quotation.testing;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.client.WireMock;
import com.github.tomakehurst.wiremock.core.WireMockConfiguration;
import com.fidiacorp.credicasa.shared.security.JwtTokenIssuer;
import org.springframework.test.context.DynamicPropertyRegistry;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.UUID;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.matchingJsonPath;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;

/**
 * Dobles HTTP reales (WireMock) de identity-subscription-service, property-catalog-service y mortgage-pricing-service
 * con sus contratos internos. Los cronogramas son fixtures generados con el método francés (liquidación 0.00).
 */
public final class Downstreams {
    public static final String SECRET = "TestOnlyJwtSigningKeyForIsolatedIntegrationTests123456789";
    public static final String V1_SIMULATION = "11111111-1111-4111-8111-111111111111";
    public static final String V2_SIMULATION = "22222222-2222-4222-8222-222222222222";
    public static final WireMockServer SERVER = new WireMockServer(WireMockConfiguration.options().dynamicPort());

    static {
        SERVER.start();
    }

    private Downstreams() {
    }

    public record Actor(UUID id, String email, String token) {}

    public static void register(DynamicPropertyRegistry registry) {
        registry.add("app.services.identity-url", SERVER::baseUrl);
        registry.add("app.services.catalog-url", SERVER::baseUrl);
        registry.add("app.services.pricing-url", SERVER::baseUrl);
    }

    public static Actor actor(String prefix, String role) {
        UUID id = UUID.randomUUID();
        String email = prefix + "-" + id + "@example.com";
        return new Actor(id, email, new JwtTokenIssuer(SECRET, 3_600_000).issue(id, List.of(role), email, "Lucía Pérez"));
    }

    public static void reset() {
        SERVER.resetAll();
        pricingHealthy();
    }

    public static void account(Actor actor, String role) {
        SERVER.stubFor(WireMock.get(urlPathEqualTo("/api/v1/internal/accounts/lookup")).withQueryParam("email", equalTo(actor.email()))
                .willReturn(json("{\"id\":\"" + actor.id() + "\",\"displayName\":\"Lucía Pérez\",\"email\":\"" + actor.email()
                        + "\",\"roles\":[\"" + role + "\"]}")));
    }

    public static UUID property(Actor owner, String price, String currency) {
        UUID id = UUID.randomUUID();
        SERVER.stubFor(WireMock.get(urlPathEqualTo("/api/v1/internal/properties/" + id)).willReturn(json("{\"id\":\"" + id + "\",\"ownerId\":\""
                + owner.id() + "\",\"title\":\"Departamento Barranco\",\"address\":\"Av. Grau 123, Barranco\",\"price\":\"" + price
                + "\",\"currency\":\"" + currency + "\"}")));
        return id;
    }

    public static void pricingHealthy() {
        SERVER.stubFor(WireMock.post(urlPathEqualTo("/api/v1/simulations/calculate")).atPriority(5).willReturn(json(fixture("pricing-v1.json")).withStatus(201)));
        SERVER.stubFor(WireMock.post(urlPathEqualTo("/api/v1/simulations/calculate")).atPriority(1)
                .withRequestBody(matchingJsonPath("$.annualInterestRate", equalTo("0.08250000"))).willReturn(json(fixture("pricing-v2.json")).withStatus(201)));
        SERVER.stubFor(WireMock.post(urlPathEqualTo("/api/v1/simulations/calculate")).atPriority(1)
                .withRequestBody(matchingJsonPath("$.annualInterestRate", equalTo("0.99000000"))).willReturn(aResponse().withStatus(400)
                        .withHeader("Content-Type", "application/problem+json").withBody("{\"status\":400,\"detail\":\"Tasa inválida de TEA\"}")));
        SERVER.stubFor(WireMock.get(urlPathEqualTo("/api/v1/simulations/" + V1_SIMULATION)).willReturn(json(fixture("pricing-v1.json"))));
        SERVER.stubFor(WireMock.get(urlPathEqualTo("/api/v1/simulations/" + V2_SIMULATION)).willReturn(json(fixture("pricing-v2.json"))));
    }

    public static void pricingDown() {
        SERVER.stubFor(WireMock.post(urlPathEqualTo("/api/v1/simulations/calculate")).atPriority(0).willReturn(aResponse().withStatus(503)));
    }

    public static String fixture(String name) {
        try (var in = Downstreams.class.getResourceAsStream("/fixtures/" + name)) {
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static com.github.tomakehurst.wiremock.client.ResponseDefinitionBuilder json(String body) {
        return aResponse().withHeader("Content-Type", "application/json").withBody(body);
    }
}
