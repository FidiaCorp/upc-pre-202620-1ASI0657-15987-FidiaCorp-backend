package com.fidiacorp.credicasa.identity.infrastructure.bootstrap;

import com.fidiacorp.credicasa.identity.application.usecase.AccountDirectoryService;
import com.fidiacorp.credicasa.identity.domain.model.Account;
import com.fidiacorp.credicasa.identity.domain.ports.out.AccountRepositoryPort;
import org.junit.jupiter.api.Test;
import org.springframework.boot.DefaultApplicationArguments;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class RealtorBootstrapRunnerTest {

    private final InMemoryAccounts accounts = new InMemoryAccounts();
    private final AccountDirectoryService directory = new AccountDirectoryService(accounts);

    @Test
    void promotesExistingAccountsAndIgnoresUnknownEmails() {
        Account ana = accounts.save(account("ana.asesora@credicasa.pe"));
        Account luis = accounts.save(account("luis.cliente@credicasa.pe"));

        new RealtorBootstrapRunner(directory, " Ana.Asesora@CrediCasa.pe , no.existe@credicasa.pe,,")
                .run(new DefaultApplicationArguments());

        assertThat(accounts.findById(ana.id()).orElseThrow().roles()).containsExactly("ROLE_REALTOR");
        assertThat(accounts.findById(luis.id()).orElseThrow().roles()).containsExactly("ROLE_CLIENT");
    }

    @Test
    void isIdempotentAcrossRestarts() {
        Account ana = accounts.save(account("ana.asesora@credicasa.pe"));
        RealtorBootstrapRunner runner = new RealtorBootstrapRunner(directory, "ana.asesora@credicasa.pe");

        runner.run(new DefaultApplicationArguments());
        runner.run(new DefaultApplicationArguments());

        assertThat(accounts.findById(ana.id()).orElseThrow().roles()).containsExactly("ROLE_REALTOR");
    }

    @Test
    void parsesCommaSeparatedListWithoutBlanksOrDuplicates() {
        RealtorBootstrapRunner runner = new RealtorBootstrapRunner(directory, "a@x.pe, ,b@x.pe,a@x.pe");

        assertThat(runner.configuredEmails()).containsExactly("a@x.pe", "b@x.pe");
    }

    private static Account account(String email) {
        return new Account(UUID.randomUUID(), "Nombre", "Apellido", email, "{noop}hash", Set.of("ROLE_CLIENT"));
    }

    private static final class InMemoryAccounts implements AccountRepositoryPort {
        private final Map<UUID, Account> byId = new HashMap<>();

        @Override
        public Optional<Account> findById(UUID id) {
            return Optional.ofNullable(byId.get(id));
        }

        @Override
        public Optional<Account> findByEmail(String email) {
            return byId.values().stream().filter(a -> a.email().equals(email)).findFirst();
        }

        @Override
        public Account save(Account account) {
            byId.put(account.id(), account);
            return account;
        }
    }
}
