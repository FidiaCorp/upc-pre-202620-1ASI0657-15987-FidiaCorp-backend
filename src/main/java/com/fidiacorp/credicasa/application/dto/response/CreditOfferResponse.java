package com.fidiacorp.credicasa.application.dto.response;
import com.fidiacorp.credicasa.domain.model.*;
import com.fidiacorp.credicasa.domain.model.Currency;
import com.fasterxml.jackson.annotation.JsonFormat;
import java.math.BigDecimal;
import java.time.*;
import java.util.UUID;
public record CreditOfferResponse(UUID id,UUID recipientId,UUID issuerId,OfferBank bankCode,String bankName,String title,
    OfferOrigin origin,OfferPropertySnapshotResponse property,
    @JsonFormat(shape=JsonFormat.Shape.STRING) BigDecimal loanAmount,Currency currency,
    @JsonFormat(shape=JsonFormat.Shape.STRING) BigDecimal annualInterestRate,
    int termMonths,LocalDate startDate,UUID simulationId,Instant createdAt) {
    public static CreditOfferResponse from(CreditOffer o) {
        return new CreditOfferResponse(o.id(),o.recipientId(),o.issuerId(),o.bankCode(),o.bankCode().displayName(),
            o.bankCode().demoTitle(),o.origin(),OfferPropertySnapshotResponse.from(o.property()),
            o.loanAmount(),o.currency(),o.annualInterestRate(),o.termMonths(),o.startDate(),o.simulationId(),o.createdAt());
    }
}
