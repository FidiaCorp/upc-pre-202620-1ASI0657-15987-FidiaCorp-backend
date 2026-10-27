package com.fidiacorp.credicasa.infrastructure.security;
import com.fidiacorp.credicasa.domain.ports.out.PasswordHashingPort;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
@Component
public class BCryptPasswordHasher implements PasswordHashingPort {
    private final PasswordEncoder encoder;
    public BCryptPasswordHasher(PasswordEncoder encoder) { this.encoder = encoder; }
    public boolean matches(String password,String hash) {
        if (password == null || password.getBytes(java.nio.charset.StandardCharsets.UTF_8).length > 72) return false;
        return encoder.matches(password,hash);
    }
    public String hash(String password) { return encoder.encode(password); }
}
