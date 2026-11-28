package com.fidiacorp.credicasa.quotation.application.ports;

import com.fidiacorp.credicasa.quotation.application.dto.external.PricingCalculateRequest;
import com.fidiacorp.credicasa.quotation.application.dto.response.OfferScheduleResponse;

import java.util.UUID;

/** mortgage-pricing-service: el cálculo financiero vive solo allí (ADR-001); se invoca con el JWT del usuario. */
public interface PricingPort {
    OfferScheduleResponse calculate(PricingCalculateRequest request);
    OfferScheduleResponse get(UUID simulationId);
}
