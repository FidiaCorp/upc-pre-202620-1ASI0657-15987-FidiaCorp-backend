package com.fidiacorp.credicasa.identity.domain.model;

import java.math.BigDecimal;
import java.util.UUID;

public record GatewayChargeRequest(String gatewayCode, UUID accountId, SubscriptionPlan plan,
                                   BigDecimal amount, String currency, String cardToken) {}
