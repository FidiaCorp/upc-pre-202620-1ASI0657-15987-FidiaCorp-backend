package com.fidiacorp.credicasa.application.dto.response;
import com.fidiacorp.credicasa.domain.model.Currency;
import com.fidiacorp.credicasa.domain.model.OfferPropertySnapshot;
import com.fasterxml.jackson.annotation.JsonFormat;
import java.math.BigDecimal;
import java.util.UUID;
public record OfferPropertySnapshotResponse(UUID id,String title,String address,
    @JsonFormat(shape=JsonFormat.Shape.STRING) @com.fasterxml.jackson.databind.annotation.JsonSerialize(using=PlainDecimalSerializer.class) BigDecimal price,Currency currency) {
    public static OfferPropertySnapshotResponse from(OfferPropertySnapshot p) {
        return new OfferPropertySnapshotResponse(p.id(),p.title(),p.address(),p.price(),p.currency());
    }
}
