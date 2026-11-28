package com.fidiacorp.credicasa.quotation.application.dto.snapshot;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fidiacorp.credicasa.quotation.application.dto.response.OfferScheduleResponse;
import com.fidiacorp.credicasa.shared.json.PlainDecimalSerializer;
import com.fidiacorp.credicasa.shared.money.Currency;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/**
 * US21/US22: contenido congelado de una versión. Se serializa en JSON canónico (propiedades en orden alfabético) y su
 * SHA-256 es el {@code snapshotHash}; PDF y XLSX se generan solo desde aquí.
 */
public record QuotationSnapshot(int schemaVersion, UUID offerId, int versionNumber, Instant issuedAt, Instant expiresAt, String bankCode,
                                String bankName, String title, UUID issuerId, UUID recipientId, Property property, Terms terms,
                                OfferScheduleResponse schedule) {
    public record Property(UUID id, String title, String address, @JsonSerialize(using = PlainDecimalSerializer.class) BigDecimal price, Currency currency) {}

    public record Terms(@JsonSerialize(using = PlainDecimalSerializer.class) BigDecimal loanAmount, Currency currency, @JsonSerialize(using = PlainDecimalSerializer.class) BigDecimal annualInterestRate, int termMonths, LocalDate startDate) {}
}
