package com.fidiacorp.credicasa.communication.infrastructure.clients;

import com.fidiacorp.credicasa.shared.error.UpstreamServiceException;
import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.client.WireMock;
import com.github.tomakehurst.wiremock.core.WireMockConfiguration;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Contrato HTTP de las API internas de identity y catalog (con X-Internal-Token) consumidas por MS-5. */
class DirectoryHttpAdaptersIT {
    static WireMockServer server;

    @BeforeAll
    static void start() {
        server = new WireMockServer(WireMockConfiguration.options().dynamicPort());
        server.start();
    }

    @AfterAll
    static void stop() {
        server.stop();
    }

    @Test
    void resolvesAccountsAndPropertiesAndTranslatesFailures() {
        DirectoryHttpAdapters adapters = new DirectoryHttpAdapters(server.baseUrl(), server.baseUrl(), "internal-secret");
        UUID account = UUID.randomUUID();
        UUID property = UUID.randomUUID();
        server.stubFor(WireMock.get(urlPathEqualTo("/api/v1/internal/accounts/" + account)).willReturn(aResponse()
                .withHeader("Content-Type", "application/json").withBody("{\"id\":\"" + account + "\",\"displayName\":\"Rosa Quispe\",\"email\":\"r@x.pe\"}")));
        server.stubFor(WireMock.get(urlPathEqualTo("/api/v1/internal/properties/" + property)).willReturn(aResponse().withStatus(200)
                .withHeader("Content-Type", "application/json").withBody("{}")));
        assertThat(adapters.find(account)).hasValueSatisfying(a -> assertThat(a.displayName()).isEqualTo("Rosa Quispe"));
        assertThat(adapters.find(UUID.randomUUID())).isEmpty();
        assertThat(adapters.isActive(property)).isTrue();
        assertThat(adapters.isActive(UUID.randomUUID())).isFalse();
        server.verify(getRequestedFor(urlPathEqualTo("/api/v1/internal/accounts/" + account)).withHeader("X-Internal-Token", equalTo("internal-secret")));
        UUID broken = UUID.randomUUID();
        server.stubFor(WireMock.get(urlPathEqualTo("/api/v1/internal/accounts/" + broken)).willReturn(aResponse().withStatus(503)));
        server.stubFor(WireMock.get(urlPathEqualTo("/api/v1/internal/properties/" + broken)).willReturn(aResponse().withStatus(500)));
        assertThatThrownBy(() -> adapters.find(broken)).isInstanceOf(UpstreamServiceException.class);
        assertThatThrownBy(() -> adapters.isActive(broken)).isInstanceOf(UpstreamServiceException.class);
    }
}
