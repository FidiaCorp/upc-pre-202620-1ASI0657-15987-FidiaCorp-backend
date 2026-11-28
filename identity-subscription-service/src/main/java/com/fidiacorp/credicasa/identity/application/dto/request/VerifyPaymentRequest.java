package com.fidiacorp.credicasa.identity.application.dto.request;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

/** US06: confirma una referencia emitida por la pasarela (US05) y activa el plan. */
public record VerifyPaymentRequest(
        @NotBlank(message = "transactionReference is required")
        @Size(max = 100, message = "transactionReference must not exceed 100 characters")
        String transactionReference,

        @NotBlank(message = "paymentMethod is required")
        @Size(max = 50, message = "paymentMethod must not exceed 50 characters")
        String paymentMethod,

        @NotNull(message = "amount is required")
        @DecimalMin(value = "0.01", message = "amount must be greater than zero")
        BigDecimal amount,

        @NotBlank(message = "currency is required")
        String currency,

        @NotBlank(message = "planName is required")
        String planName
) {}
