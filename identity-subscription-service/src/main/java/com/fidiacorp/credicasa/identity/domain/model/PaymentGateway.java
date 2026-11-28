package com.fidiacorp.credicasa.identity.domain.model;

import java.math.BigDecimal;
import java.util.List;

/** Pasarela de pago disponible en modo sandbox (US05). */
public record PaymentGateway(String code, String displayName, List<String> currencies,
                             BigDecimal commissionPercent, BigDecimal fixedFee, boolean sandbox) {
    public PaymentGateway { currencies = List.copyOf(currencies); }
    public boolean supportsCurrency(String currency) { return currency != null && currencies.contains(currency); }
}
