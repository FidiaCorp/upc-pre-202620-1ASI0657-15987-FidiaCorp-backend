package com.fidiacorp.credicasa.infrastructure.security;
import com.fidiacorp.credicasa.domain.model.Account;
import com.fidiacorp.credicasa.domain.ports.out.AccountRepositoryPort;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.*;
import org.springframework.stereotype.Service;
import java.util.Locale;
import java.util.UUID;
@Service
public class CustomUserDetailsService implements UserDetailsService {
    private final AccountRepositoryPort repository;
    public CustomUserDetailsService(AccountRepositoryPort repository) { this.repository = repository; }
    @Override
    public UserDetails loadUserByUsername(String email) {
        return toUser(repository.findByEmail(email.trim().toLowerCase(Locale.ROOT))
            .orElseThrow(() -> new UsernameNotFoundException("Credenciales inválidas.")));
    }
    public UserDetails loadById(String id) {
        try {
            return toUser(repository.findById(UUID.fromString(id))
                .orElseThrow(() -> new UsernameNotFoundException("Cuenta no encontrada.")));
        } catch (IllegalArgumentException ex) { throw new UsernameNotFoundException("Cuenta no encontrada.", ex); }
    }
    private UserDetails toUser(Account account) {
        return new User(account.id().toString(), account.passwordHash(), account.roles().stream()
            .map(SimpleGrantedAuthority::new).toList());
    }
}
