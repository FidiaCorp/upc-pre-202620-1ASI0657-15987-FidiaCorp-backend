package com.fidiacorp.credicasa.identity.application.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/** US05: inicia un cobro sandbox con la pasarela elegida. El importe se deriva de la tarifa oficial del plan. */
public record CheckoutRequest(
        @NotBlank(message = "gatewayCode is required")
        @Pattern(regexp = "(?i)CULQI|IZIPAY|STRIPE", message = "gatewayCode must be CULQI, IZIPAY or STRIPE")
        String gatewayCode,

        @NotBlank(message = "planName is required")
        String planName,

        @NotBlank(message = "cardToken is required")
        @Size(max = 64, message = "cardToken must not exceed 64 characters")
        @Pattern(regexp = "[A-Za-z0-9_\\-]{6,64}", message = "cardToken has an invalid format")
        String cardToken,

        String currency
) {}
