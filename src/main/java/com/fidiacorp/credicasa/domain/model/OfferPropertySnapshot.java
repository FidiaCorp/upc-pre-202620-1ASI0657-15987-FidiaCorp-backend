package com.fidiacorp.credicasa.domain.model;
import java.math.BigDecimal;
import java.util.UUID;
public record OfferPropertySnapshot(UUID id,String title,String address,BigDecimal price,Currency currency) {}
