package com.fidiacorp.credicasa.communication.testing;

import com.fidiacorp.credicasa.communication.application.ports.AccountDirectoryPort;
import com.fidiacorp.credicasa.communication.application.ports.PropertyDirectoryPort;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;

import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/** Doble en memoria de los directorios de identity y catalog (los adaptadores HTTP se prueban aparte con WireMock). */
@Primary
@Component
public class InMemoryDirectories implements AccountDirectoryPort, PropertyDirectoryPort {
    private final StubAccounts accounts;
    private final Set<UUID> activeProperties = ConcurrentHashMap.newKeySet();

    public InMemoryDirectories(StubAccounts accounts) {
        this.accounts = accounts;
    }

    public UUID activeProperty() {
        UUID id = UUID.randomUUID();
        activeProperties.add(id);
        return id;
    }

    public void retire(UUID propertyId) {
        activeProperties.remove(propertyId);
    }

    @Override
    public Optional<Account> find(UUID accountId) {
        return accounts.findById(accountId).map(a -> new Account(a.id, (a.firstName + " " + a.lastName).strip()));
    }

    @Override
    public boolean isActive(UUID propertyId) {
        return activeProperties.contains(propertyId);
    }
}
