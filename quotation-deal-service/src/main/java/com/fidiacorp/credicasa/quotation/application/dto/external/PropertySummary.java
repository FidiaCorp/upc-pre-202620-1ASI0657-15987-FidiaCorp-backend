package com.fidiacorp.credicasa.quotation.application.dto.external;

import com.fidiacorp.credicasa.shared.money.Currency;

import java.math.BigDecimal;
import java.util.UUID;

public record PropertySummary(UUID id, UUID ownerId, String title, String address, BigDecimal price, Currency currency) {
}
