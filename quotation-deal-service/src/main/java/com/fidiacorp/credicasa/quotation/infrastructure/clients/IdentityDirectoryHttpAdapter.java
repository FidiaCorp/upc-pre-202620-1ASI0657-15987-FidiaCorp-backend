package com.fidiacorp.credicasa.quotation.infrastructure.clients;

import com.fidiacorp.credicasa.quotation.application.dto.external.AccountSummary;
import com.fidiacorp.credicasa.quotation.application.ports.IdentityDirectoryPort;
import com.fidiacorp.credicasa.shared.error.ResourceNotFoundException;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.util.Optional;

@Component
public class IdentityDirectoryHttpAdapter implements IdentityDirectoryPort {
    private final RestClient client;

    public IdentityDirectoryHttpAdapter(@Qualifier("identityClient") RestClient client) {
        this.client = client;
    }

    @Override
    @CircuitBreaker(name = "identityService")
    public Optional<AccountSummary> findByEmail(String email) {
        try {
            return Optional.ofNullable(client.get().uri(u -> u.path("/api/v1/internal/accounts/lookup").queryParam("email", email).build())
                    .retrieve().body(AccountSummary.class));
        } catch (RestClientException e) {
            RuntimeException translated = DownstreamErrors.translate("identity-subscription-service", e);
            if (translated instanceof ResourceNotFoundException) return Optional.empty();
            throw translated;
        }
    }
}
