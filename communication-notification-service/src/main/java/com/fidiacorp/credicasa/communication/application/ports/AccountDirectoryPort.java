package com.fidiacorp.credicasa.communication.application.ports;

import java.util.Optional;
import java.util.UUID;

/** Directorio de identity-subscription-service por API interna (no hay FK física hacia accounts). */
public interface AccountDirectoryPort {
    record Account(UUID id, String displayName) {}

    Optional<Account> find(UUID accountId);
}
