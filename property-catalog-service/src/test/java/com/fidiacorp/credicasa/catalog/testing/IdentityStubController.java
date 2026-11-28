package com.fidiacorp.credicasa.catalog.testing;

import com.fidiacorp.credicasa.shared.security.JwtTokenIssuer;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

/**
 * Doble de prueba (solo src/test) de /api/v1/auth/register y /api/v1/auth/login de identity-subscription-service:
 * emite JWT HS256 reales con {@link JwtTokenIssuer} del shared kernel, como haría el servicio de identidad.
 */
@io.swagger.v3.oas.annotations.Hidden
@RestController
public class IdentityStubController {
    private final StubAccounts accounts;
    private final JwtTokenIssuer issuer;

    public IdentityStubController(StubAccounts accounts, @Value("${app.security.jwt.secret}") String secret) {
        this.accounts = accounts;
        this.issuer = new JwtTokenIssuer(secret, 3_600_000);
    }

    @Configuration
    public static class StubSecurity {
        @Bean
        @Order(0)
        SecurityFilterChain identityStubChain(HttpSecurity http) throws Exception {
            return http.securityMatcher("/api/v1/auth/register", "/api/v1/auth/login")
                    .csrf(AbstractHttpConfigurer::disable).authorizeHttpRequests(a -> a.anyRequest().permitAll()).build();
        }
    }

    @PostMapping("/api/v1/auth/register")
    public ResponseEntity<Map<String, Object>> register(@RequestBody Map<String, Object> body) {
        String email = String.valueOf(body.get("email")).strip().toLowerCase(Locale.ROOT);
        if (accounts.findByEmail(email).isPresent()) return ResponseEntity.status(HttpStatus.CONFLICT).build();
        StubAccount account = new StubAccount();
        account.id = UUID.randomUUID();
        account.firstName = String.valueOf(body.getOrDefault("firstName", "Ana")).strip();
        account.lastName = String.valueOf(body.getOrDefault("lastName", "Perez")).strip();
        account.email = email;
        account.password = String.valueOf(body.get("password"));
        accounts.saveAndFlush(account);
        return ResponseEntity.status(HttpStatus.CREATED).body(profile(account));
    }

    @PostMapping("/api/v1/auth/login")
    public ResponseEntity<Map<String, Object>> login(@RequestBody Map<String, Object> body) {
        return accounts.findByEmail(String.valueOf(body.get("email")))
                .filter(a -> a.password.equals(String.valueOf(body.get("password"))))
                .map(a -> {
                    Map<String, Object> token = new LinkedHashMap<>();
                    token.put("token", token(a));
                    token.put("type", "Bearer");
                    token.put("email", a.email);
                    token.put("roles", roles(a));
                    token.put("expiresIn", 3_600_000);
                    return ResponseEntity.ok(token);
                })
                .orElseGet(() -> ResponseEntity.status(HttpStatus.UNAUTHORIZED).build());
    }

    public String token(StubAccount account) {
        return issuer.issue(account.id, roles(account), account.email, (account.firstName + " " + account.lastName).strip());
    }

    private static List<String> roles(StubAccount account) {
        return Arrays.stream(account.roles.split(",")).map(String::strip).filter(r -> !r.isEmpty()).toList();
    }

    private static Map<String, Object> profile(StubAccount account) {
        Map<String, Object> profile = new LinkedHashMap<>();
        profile.put("id", account.id);
        profile.put("firstName", account.firstName);
        profile.put("lastName", account.lastName);
        profile.put("email", account.email);
        profile.put("roles", roles(account));
        return profile;
    }
}
