package com.fidiacorp.credicasa.application.dto.request;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;

public record VerifyPaymentRequest(
        @NotBlank(message = "transactionReference is required")
        String transactionReference,

        @NotBlank(message = "paymentMethod is required")
        String paymentMethod,

        @NotNull(message = "amount is required")
        @DecimalMin(value = "0.01", message = "amount must be greater than zero")
        BigDecimal amount,

        @NotBlank(message = "currency is required")
        String currency,

        String planName
) {}
