package com.fidiacorp.credicasa.catalog.application.dto.response;
import com.fidiacorp.credicasa.shared.money.Currency;
import com.fidiacorp.credicasa.shared.json.PlainDecimalSerializer;
import com.fidiacorp.credicasa.catalog.domain.model.Property;
import com.fidiacorp.credicasa.catalog.domain.model.PropertyLegitimacyReport;
import com.fidiacorp.credicasa.catalog.domain.model.PropertyLegitimacyStatus;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.*;
public record PropertyResponse(UUID id, UUID ownerId, String title, String address, String description,
        @com.fasterxml.jackson.annotation.JsonFormat(shape=com.fasterxml.jackson.annotation.JsonFormat.Shape.STRING) @com.fasterxml.jackson.databind.annotation.JsonSerialize(using=PlainDecimalSerializer.class) BigDecimal price, Currency currency, Set<String> tags, Instant createdAt,
        PropertyLegitimacyStatus legitimacyStatus, Instant legitimacyVerifiedAt, String legitimacyNotes, String certificateHash) {
    public static PropertyResponse from(Property p) {
        PropertyLegitimacyReport legitimacy=p.legitimacy()==null?PropertyLegitimacyReport.pending(p.id()):p.legitimacy();
        return new PropertyResponse(p.id(),p.ownerId(),p.title(),p.address(),p.description(),
            p.price(),p.currency(),p.tags(),p.createdAt(),legitimacy.getStatus(),legitimacy.getVerifiedAt(),
            legitimacy.getVerificationNotes(),legitimacy.getCertificateHash());
    }
}
