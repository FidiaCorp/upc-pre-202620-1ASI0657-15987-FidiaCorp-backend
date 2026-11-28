package com.fidiacorp.credicasa.shared.error;

/** Solicitud bien formada que incumple una regla de negocio verificable (HTTP 422). */
public class BusinessRuleViolationException extends RuntimeException {
    public BusinessRuleViolationException(String message) {
        super(message);
    }
}
