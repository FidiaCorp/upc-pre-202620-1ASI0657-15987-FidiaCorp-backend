package com.fidiacorp.credicasa.identity.infrastructure.security;

import com.fidiacorp.credicasa.identity.domain.model.Account;
import com.fidiacorp.credicasa.identity.domain.ports.out.AccountRepositoryPort;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

import java.util.Locale;

@Service
public class CustomUserDetailsService implements UserDetailsService {
    private final AccountRepositoryPort repository;

    public CustomUserDetailsService(AccountRepositoryPort repository) {
        this.repository = repository;
    }

    @Override
    public UserDetails loadUserByUsername(String email) {
        return toUser(repository.findByEmail(email.trim().toLowerCase(Locale.ROOT))
                .orElseThrow(() -> new UsernameNotFoundException("Credenciales inválidas.")));
    }

    private UserDetails toUser(Account account) {
        return new AccountPrincipal(account.id().toString(), account.passwordHash(),
                account.roles().stream().sorted().map(SimpleGrantedAuthority::new).toList(), account.email(),
                (account.firstName() + " " + account.lastName()).strip());
    }
}
