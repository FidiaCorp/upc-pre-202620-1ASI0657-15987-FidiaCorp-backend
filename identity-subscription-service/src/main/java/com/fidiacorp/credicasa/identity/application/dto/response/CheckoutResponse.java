package com.fidiacorp.credicasa.identity.application.dto.response;
import com.fidiacorp.credicasa.shared.json.PlainDecimalSerializer;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fidiacorp.credicasa.identity.domain.model.GatewayChargeResult;
import com.fidiacorp.credicasa.identity.domain.model.GatewayChargeStatus;

import java.math.BigDecimal;

public record CheckoutResponse(
        String gatewayCode,
        String planName,
        GatewayChargeStatus status,
        String transactionReference,
        String authorizationCode,
        String declineCode,
        @JsonSerialize(using = PlainDecimalSerializer.class) BigDecimal amount,
        @JsonSerialize(using = PlainDecimalSerializer.class) BigDecimal commission,
        String currency,
        boolean retryAllowed,
        String message
) {
    public static CheckoutResponse from(String plan, GatewayChargeResult r) {
        return new CheckoutResponse(r.gatewayCode(), plan, r.status(), r.transactionReference(), r.authorizationCode(), r.declineCode(),
                r.amount(), r.commission(), r.currency(), !r.approved(), r.message());
    }
}
