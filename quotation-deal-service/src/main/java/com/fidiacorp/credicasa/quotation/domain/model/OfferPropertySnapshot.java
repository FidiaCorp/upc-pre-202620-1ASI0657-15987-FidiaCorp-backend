package com.fidiacorp.credicasa.quotation.domain.model;
import com.fidiacorp.credicasa.shared.money.Currency;
import java.math.BigDecimal;
import java.util.UUID;
public record OfferPropertySnapshot(UUID id,String title,String address,BigDecimal price,Currency currency) {}
