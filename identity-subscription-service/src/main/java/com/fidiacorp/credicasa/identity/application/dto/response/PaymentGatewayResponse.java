package com.fidiacorp.credicasa.identity.application.dto.response;
import com.fidiacorp.credicasa.shared.json.PlainDecimalSerializer;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

public record PaymentGatewayResponse(
        String code,
        String displayName,
        List<String> currencies,
        @JsonSerialize(using = PlainDecimalSerializer.class) BigDecimal commissionPercent,
        @JsonSerialize(using = PlainDecimalSerializer.class) BigDecimal fixedFee,
        boolean sandbox,
        Map<String, String> planFees
) {}
