package com.fidiacorp.credicasa.quotation.infrastructure.clients;

import com.fidiacorp.credicasa.quotation.application.dto.external.PropertySummary;
import com.fidiacorp.credicasa.quotation.application.ports.PropertyCatalogPort;
import com.fidiacorp.credicasa.shared.error.ResourceNotFoundException;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.util.Optional;
import java.util.UUID;

@Component
public class PropertyCatalogHttpAdapter implements PropertyCatalogPort {
    private final RestClient client;

    public PropertyCatalogHttpAdapter(@Qualifier("catalogClient") RestClient client) {
        this.client = client;
    }

    @Override
    @CircuitBreaker(name = "catalogService")
    public Optional<PropertySummary> findActive(UUID propertyId) {
        try {
            return Optional.ofNullable(client.get().uri("/api/v1/internal/properties/{id}", propertyId).retrieve().body(PropertySummary.class));
        } catch (RestClientException e) {
            RuntimeException translated = DownstreamErrors.translate("property-catalog-service", e);
            if (translated instanceof ResourceNotFoundException) return Optional.empty();
            throw translated;
        }
    }
}
