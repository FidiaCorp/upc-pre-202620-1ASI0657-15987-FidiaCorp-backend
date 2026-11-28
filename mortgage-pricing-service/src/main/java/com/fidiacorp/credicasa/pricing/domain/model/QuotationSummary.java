package com.fidiacorp.credicasa.pricing.domain.model;

import com.fidiacorp.credicasa.shared.money.Currency;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

/** US20: fila del historial de simulaciones propias. */
public record QuotationSummary(UUID quotationId, LocalDateTime createdAt, Currency currency, BigDecimal loanAmount,
                               int termMonths, String rateType, BigDecimal inputRate, BigDecimal tea, BigDecimal tcea,
                               BigDecimal totalInterest, BigDecimal totalCost, String propertyAddress) {
}
