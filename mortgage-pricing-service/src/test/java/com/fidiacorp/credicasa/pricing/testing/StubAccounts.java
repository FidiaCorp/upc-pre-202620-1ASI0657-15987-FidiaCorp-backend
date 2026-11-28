package com.fidiacorp.credicasa.pricing.testing;

import org.springframework.stereotype.Component;

import java.util.Collection;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Directorio en memoria del doble de identity-subscription-service. En producción las cuentas viven en identity_db;
 * aquí solo sirven para emitir JWT reales (misma librería compartida) y simular la API interna de cuentas.
 */
@Component
public class StubAccounts {
    private final Map<UUID, StubAccount> accounts = new ConcurrentHashMap<>();

    public Optional<StubAccount> findById(UUID id) {
        return Optional.ofNullable(accounts.get(id));
    }

    public Optional<StubAccount> findByEmail(String email) {
        String normalized = email == null ? "" : email.strip().toLowerCase(Locale.ROOT);
        return accounts.values().stream().filter(a -> a.email.equals(normalized)).findFirst();
    }

    public StubAccount saveAndFlush(StubAccount account) {
        accounts.put(account.id, account);
        return account;
    }

    public Collection<StubAccount> findAll() {
        return accounts.values();
    }

    public void deleteAll() {
        accounts.clear();
    }

    public long count() {
        return accounts.size();
    }
}
