package com.fidiacorp.credicasa.identity.application.usecase;

import com.fidiacorp.credicasa.identity.application.dto.response.AccountSummaryResponse;
import com.fidiacorp.credicasa.identity.domain.model.Account;
import com.fidiacorp.credicasa.identity.domain.ports.out.AccountRepositoryPort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashSet;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * Directorio de cuentas para la colaboración entre microservicios sin claves foráneas físicas (ADR-001/PA-05): otros
 * contextos guardan UUID lógicos y validan existencia/rol por esta API interna.
 */
@Service
@Transactional(readOnly = true)
public class AccountDirectoryService {
    private final AccountRepositoryPort accounts;

    public AccountDirectoryService(AccountRepositoryPort accounts) {
        this.accounts = accounts;
    }

    public Optional<AccountSummaryResponse> summary(UUID id) {
        return accounts.findById(id).map(AccountSummaryResponse::from);
    }

    public boolean exists(UUID id) {
        return accounts.findById(id).isPresent();
    }

    public Optional<AccountSummaryResponse> findByEmail(String email) {
        if (email == null || email.isBlank()) return Optional.empty();
        return accounts.findByEmail(email.strip().toLowerCase(Locale.ROOT)).map(AccountSummaryResponse::from);
    }

    /** Solo para el fixture E2E local: concede ROLE_REALTOR (no existe asignación pública de este rol). */
    @Transactional
    public Optional<AccountSummaryResponse> grantRealtor(UUID id) {
        return accounts.findById(id).map(account -> {
            Set<String> roles = new HashSet<>(account.roles());
            roles.add("ROLE_REALTOR");
            roles.remove("ROLE_CLIENT");
            return AccountSummaryResponse.from(accounts.save(new Account(account.id(), account.firstName(), account.lastName(),
                    account.email(), account.passwordHash(), roles)));
        });
    }
}
