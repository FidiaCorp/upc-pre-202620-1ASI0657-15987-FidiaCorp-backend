package com.fidiacorp.credicasa.quotation.application.ports;

import com.fidiacorp.credicasa.quotation.application.dto.external.PropertySummary;

import java.util.Optional;
import java.util.UUID;

/** Catálogo de property-catalog-service (API interna, sin FK física). */
public interface PropertyCatalogPort {
    Optional<PropertySummary> findActive(UUID propertyId);
}
