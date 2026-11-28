package com.fidiacorp.credicasa.shared.security;

/** Política opcional de revocación de tokens (p. ej. lista de revocación persistida en identity_db). */
@FunctionalInterface
public interface RevocationChecker {
    RevocationChecker NONE = token -> false;

    boolean isRevoked(String token);
}
