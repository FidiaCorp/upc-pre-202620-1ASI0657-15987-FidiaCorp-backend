package com.fidiacorp.credicasa.application.usecase;
import com.fidiacorp.credicasa.application.dto.request.RegisterAccountRequest;
import com.fidiacorp.credicasa.application.dto.request.UpdateProfileRequest;
import com.fidiacorp.credicasa.application.dto.response.AccountProfileResponse;
import com.fidiacorp.credicasa.domain.model.Account;
import com.fidiacorp.credicasa.domain.model.EmailAlreadyRegisteredException;
import com.fidiacorp.credicasa.domain.ports.out.AccountRepositoryPort;
import com.fidiacorp.credicasa.domain.ports.out.PasswordHashingPort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.Set;
import java.util.UUID;
@Service
public class AccountService {
    private final AccountRepositoryPort repository;
    private final PasswordHashingPort passwords;
    public AccountService(AccountRepositoryPort repository, PasswordHashingPort passwords) {
        this.repository = repository; this.passwords = passwords;
    }
    @Transactional
    public AccountProfileResponse register(RegisterAccountRequest request) {
        if (repository.findByEmail(request.email()).isPresent()) throw new EmailAlreadyRegisteredException();
        return AccountProfileResponse.from(repository.save(new Account(UUID.randomUUID(), request.firstName(),
            request.lastName(), request.email(), passwords.hash(request.password()), Set.of("ROLE_CLIENT"))));
    }
    @Transactional(readOnly=true)
    public AccountProfileResponse getProfile(UUID id) { return AccountProfileResponse.from(requireAccount(id)); }
    @Transactional
    public AccountProfileResponse updateProfile(UUID id, UpdateProfileRequest request) {
        Account current = requireAccount(id);
        repository.findByEmail(request.email()).filter(a -> !a.id().equals(id))
            .ifPresent(a -> { throw new EmailAlreadyRegisteredException(); });
        return AccountProfileResponse.from(repository.save(new Account(id, request.firstName(), request.lastName(),
            request.email(), current.passwordHash(), current.roles())));
    }
    private Account requireAccount(UUID id) {
        return repository.findById(id).orElseThrow(() -> new IllegalArgumentException("Cuenta no encontrada."));
    }
}
