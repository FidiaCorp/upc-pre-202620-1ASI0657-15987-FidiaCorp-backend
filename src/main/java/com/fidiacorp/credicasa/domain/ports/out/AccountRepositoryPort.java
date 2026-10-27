package com.fidiacorp.credicasa.domain.ports.out;
import com.fidiacorp.credicasa.domain.model.Account;
import java.util.Optional;
import java.util.UUID;
public interface AccountRepositoryPort {
    Optional<Account> findById(UUID id);
    Optional<Account> findByEmail(String email);
    Account save(Account account);
}
