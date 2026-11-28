package com.fidiacorp.credicasa.quotation.infrastructure.clients;

import com.fidiacorp.credicasa.quotation.application.dto.external.PricingCalculateRequest;
import com.fidiacorp.credicasa.quotation.application.dto.response.OfferScheduleResponse;
import com.fidiacorp.credicasa.quotation.application.ports.PricingPort;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.util.UUID;

@Component
public class PricingHttpAdapter implements PricingPort {
    private final RestClient client;

    public PricingHttpAdapter(@Qualifier("pricingClient") RestClient client) {
        this.client = client;
    }

    @Override
    @CircuitBreaker(name = "pricingService")
    public OfferScheduleResponse calculate(PricingCalculateRequest request) {
        try {
            return client.post().uri("/api/v1/simulations/calculate").contentType(MediaType.APPLICATION_JSON).body(request)
                    .retrieve().body(OfferScheduleResponse.class);
        } catch (RestClientException e) {
            throw DownstreamErrors.translate("mortgage-pricing-service", e);
        }
    }

    @Override
    @CircuitBreaker(name = "pricingService")
    public OfferScheduleResponse get(UUID simulationId) {
        try {
            return client.get().uri("/api/v1/simulations/{id}", simulationId).retrieve().body(OfferScheduleResponse.class);
        } catch (RestClientException e) {
            throw DownstreamErrors.translate("mortgage-pricing-service", e);
        }
    }
}
