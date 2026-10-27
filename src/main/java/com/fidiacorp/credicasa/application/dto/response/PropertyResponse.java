package com.fidiacorp.credicasa.application.dto.response;
import com.fidiacorp.credicasa.domain.model.Property;
import com.fidiacorp.credicasa.domain.model.Currency;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.*;
public record PropertyResponse(UUID id, UUID ownerId, String title, String address, String description,
        @com.fasterxml.jackson.annotation.JsonFormat(shape=com.fasterxml.jackson.annotation.JsonFormat.Shape.STRING) BigDecimal price, Currency currency, Set<String> tags, Instant createdAt) {
    public static PropertyResponse from(Property p) {
        return new PropertyResponse(p.id(),p.ownerId(),p.title(),p.address(),p.description(),
            p.price(),p.currency(),p.tags(),p.createdAt());
    }
}
