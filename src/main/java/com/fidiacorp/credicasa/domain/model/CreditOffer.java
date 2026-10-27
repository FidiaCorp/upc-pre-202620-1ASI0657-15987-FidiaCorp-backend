package com.fidiacorp.credicasa.domain.model;
import java.math.BigDecimal;
import java.time.*;
import java.util.UUID;
public record CreditOffer(UUID id,UUID recipientId,UUID issuerId,OfferBank bankCode,OfferOrigin origin,
    OfferPropertySnapshot property,BigDecimal loanAmount,Currency currency,BigDecimal annualInterestRate,
    int termMonths,LocalDate startDate,UUID simulationId,Instant createdAt) {}
