package com.fidiacorp.credicasa.quotation.application.ports;

import com.fidiacorp.credicasa.quotation.application.dto.external.AccountSummary;

import java.util.Optional;

/** Directorio de cuentas de identity-subscription-service (API interna, sin FK física). */
public interface IdentityDirectoryPort {
    Optional<AccountSummary> findByEmail(String email);
}
