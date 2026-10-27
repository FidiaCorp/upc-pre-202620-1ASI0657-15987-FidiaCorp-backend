package com.fidiacorp.credicasa.infrastructure.security;
import com.fidiacorp.credicasa.domain.ports.out.PasswordHashingPort;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
@Component
public class BCryptPasswordHasher implements PasswordHashingPort {
    private final PasswordEncoder encoder;
    public BCryptPasswordHasher(PasswordEncoder encoder) { this.encoder = encoder; }
    public String hash(String password) { return encoder.encode(password); }
}
