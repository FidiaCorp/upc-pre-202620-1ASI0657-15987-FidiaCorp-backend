package com.fidiacorp.credicasa.identity.domain.model;

/** Pago no verificable o inconsistente con la tarifa oficial (HTTP 422). */
public class PaymentRejectedException extends RuntimeException {
    public PaymentRejectedException(String message) { super(message); }
}
