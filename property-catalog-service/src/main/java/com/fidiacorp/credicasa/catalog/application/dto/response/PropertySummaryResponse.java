package com.fidiacorp.credicasa.catalog.application.dto.response;

import com.fidiacorp.credicasa.catalog.domain.model.Property;
import com.fidiacorp.credicasa.shared.json.PlainDecimalSerializer;
import com.fidiacorp.credicasa.shared.money.Currency;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;

import java.math.BigDecimal;
import java.util.UUID;

/** Vista interna de un inmueble activo para quotation-deal y communication-notification (UUID lógico, sin FK). */
public record PropertySummaryResponse(UUID id, UUID ownerId, String title, String address,
                                      @JsonSerialize(using = PlainDecimalSerializer.class) BigDecimal price, Currency currency) {
    public static PropertySummaryResponse from(Property property) {
        return new PropertySummaryResponse(property.id(), property.ownerId(), property.title(), property.address(), property.price(),
                property.currency());
    }
}
