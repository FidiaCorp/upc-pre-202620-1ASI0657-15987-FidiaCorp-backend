package com.fidiacorp.credicasa.quotation.application.dto.response;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fidiacorp.credicasa.quotation.application.dto.snapshot.QuotationSnapshot;
import com.fidiacorp.credicasa.shared.json.PlainDecimalSerializer;
import com.fidiacorp.credicasa.shared.money.Currency;
import io.swagger.v3.oas.annotations.media.Schema;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Schema(description = "US21: versión inmutable de la cotización (snapshot sellado con SHA-256)")
public record QuotationVersionResponse(UUID id, UUID offerId, int versionNumber, Currency currency, @JsonSerialize(using = PlainDecimalSerializer.class) BigDecimal loanAmount,
                                       @JsonSerialize(using = PlainDecimalSerializer.class) BigDecimal annualInterestRate, int termMonths, @JsonSerialize(using = PlainDecimalSerializer.class) BigDecimal monthlyInstallment, @JsonSerialize(using = PlainDecimalSerializer.class) BigDecimal tcea,
                                       @JsonSerialize(using = PlainDecimalSerializer.class) BigDecimal totalInterest, @JsonSerialize(using = PlainDecimalSerializer.class) BigDecimal totalCost, String snapshotHash, boolean intact, boolean expired,
                                       UUID issuedBy, Instant issuedAt, Instant expiresAt, QuotationSnapshot snapshot) {
}
