package com.fidiacorp.credicasa.identity.domain.model;

import java.math.BigDecimal;

/** Respuesta de la pasarela: aprobada (con referencia firmada) o rechazada (con código de rechazo). */
public record GatewayChargeResult(String gatewayCode, GatewayChargeStatus status, String transactionReference,
                                  String authorizationCode, String declineCode, BigDecimal amount,
                                  BigDecimal commission, String currency, String message) {
    public boolean approved() { return status == GatewayChargeStatus.APPROVED; }
}
