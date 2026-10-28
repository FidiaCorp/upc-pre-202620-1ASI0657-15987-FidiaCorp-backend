package com.fidiacorp.credicasa.infrastructure.persistence;
import com.fidiacorp.credicasa.domain.model.Account;
import com.fidiacorp.credicasa.domain.model.EmailAlreadyRegisteredException;
import com.fidiacorp.credicasa.domain.ports.out.AccountRepositoryPort;
import com.fidiacorp.credicasa.infrastructure.persistence.entity.AccountJpaEntity;
import com.fidiacorp.credicasa.infrastructure.persistence.repository.AccountJpaRepository;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Repository;
import java.util.*;
@Repository
public class AccountRepositoryAdapter implements AccountRepositoryPort {
    private final AccountJpaRepository repository;
    public AccountRepositoryAdapter(AccountJpaRepository repository) { this.repository = repository; }
    public Optional<Account> findById(UUID id) { return repository.findById(id).map(this::map); }
    public Optional<Account> findByEmail(String email) { return repository.findByEmail(email).map(this::map); }
    public Account save(Account account) {
        AccountJpaEntity entity = repository.findById(account.id()).orElseGet(AccountJpaEntity::new);
        entity.id = account.id(); entity.firstName = account.firstName(); entity.lastName = account.lastName();
        entity.email = account.email(); entity.passwordHash = account.passwordHash();
        entity.roles = String.join(",", new TreeSet<>(account.roles()));
        try { return map(repository.saveAndFlush(entity)); }
        catch (DataIntegrityViolationException ex) { throw new EmailAlreadyRegisteredException(); }
    }
    private Account map(AccountJpaEntity e) {
        e=org.hibernate.Hibernate.unproxy(e,AccountJpaEntity.class);
        return new Account(e.id, e.firstName, e.lastName, e.email, e.passwordHash, Set.of(e.roles.split(",")));
    }
}
