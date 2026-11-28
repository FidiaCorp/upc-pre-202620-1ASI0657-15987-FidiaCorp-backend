package com.fidiacorp.credicasa.identity.domain.ports.out;

import com.fidiacorp.credicasa.identity.domain.model.GatewayChargeRequest;
import com.fidiacorp.credicasa.identity.domain.model.GatewayChargeResult;
import com.fidiacorp.credicasa.identity.domain.model.PaymentGateway;
import com.fidiacorp.credicasa.identity.domain.model.SubscriptionPlan;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** Puerto de salida hacia procesadores de pago (Culqi, Izipay, Stripe) — US05/US06. */
public interface PaymentGatewayPort {
    List<PaymentGateway> availableGateways();
    Optional<PaymentGateway> findGateway(String code);
    GatewayChargeResult charge(GatewayChargeRequest request);
    /** Comprueba que la referencia fue emitida por la pasarela para esa cuenta, plan, importe y moneda. */
    boolean isAuthentic(String transactionReference, String gatewayCode, UUID accountId, SubscriptionPlan plan,
                        BigDecimal amount, String currency);
}
