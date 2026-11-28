package com.fidiacorp.credicasa.communication.infrastructure.clients;

import com.fidiacorp.credicasa.communication.application.ports.AccountDirectoryPort;
import com.fidiacorp.credicasa.communication.application.ports.PropertyDirectoryPort;
import com.fidiacorp.credicasa.shared.error.UpstreamServiceException;
import com.fidiacorp.credicasa.shared.web.OutboundHeadersInterceptor;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.util.Optional;
import java.util.UUID;

/**
 * Adaptadores HTTP hacia identity y catalog (API interna con JWT del usuario + X-Internal-Token), protegidos con
 * Resilience4j. 404 significa "no existe"; red/5xx → 503 y cuentan para el circuito.
 */
@Component
public class DirectoryHttpAdapters implements AccountDirectoryPort, PropertyDirectoryPort {
    record AccountView(UUID id, String displayName) {}

    private final RestClient identity;
    private final RestClient catalog;

    public DirectoryHttpAdapters(@Value("${app.services.identity-url}") String identityUrl, @Value("${app.services.catalog-url}") String catalogUrl,
                                 @Value("${app.internal.token:}") String internalToken) {
        this.identity = client(identityUrl, internalToken);
        this.catalog = client(catalogUrl, internalToken);
    }

    private static RestClient client(String url, String token) {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(1000);
        factory.setReadTimeout(3000);
        return RestClient.builder().baseUrl(url).requestFactory(factory).requestInterceptor(new OutboundHeadersInterceptor(token)).build();
    }

    @Override
    @CircuitBreaker(name = "identityService")
    public Optional<Account> find(UUID accountId) {
        try {
            AccountView view = identity.get().uri("/api/v1/internal/accounts/{id}", accountId).retrieve().body(AccountView.class);
            return Optional.ofNullable(view).map(v -> new Account(v.id(), v.displayName()));
        } catch (HttpClientErrorException.NotFound e) {
            return Optional.empty();
        } catch (RestClientException e) {
            throw new UpstreamServiceException("identity-subscription-service no disponible temporalmente.", e);
        }
    }

    @Override
    @CircuitBreaker(name = "catalogService")
    public boolean isActive(UUID propertyId) {
        try {
            catalog.get().uri("/api/v1/internal/properties/{id}", propertyId).retrieve().toBodilessEntity();
            return true;
        } catch (HttpClientErrorException.NotFound e) {
            return false;
        } catch (RestClientException e) {
            throw new UpstreamServiceException("property-catalog-service no disponible temporalmente.", e);
        }
    }
}
