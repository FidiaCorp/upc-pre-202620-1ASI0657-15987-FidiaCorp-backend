package com.fidiacorp.credicasa.identity.infrastructure.security;

import com.fidiacorp.credicasa.shared.security.JwtClaims;
import com.fidiacorp.credicasa.shared.security.JwtTokenIssuer;
import com.fidiacorp.credicasa.shared.security.JwtTokenValidator;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

/**
 * Driver 1: Seguridad (ASR-SEC). Único emisor de JWT HS256 de la plataforma; delega en la librería compartida
 * ({@link JwtTokenIssuer}/{@link JwtTokenValidator}) que el resto de microservicios usa solo para validar.
 */
@Component
public class JwtTokenProvider {
    private final JwtTokenIssuer issuer;
    private final JwtTokenValidator validator;

    public JwtTokenProvider(@Value("${app.security.jwt.secret}") String secret,
                            @Value("${app.security.jwt.expiration-ms:86400000}") long expirationMs) {
        this.issuer = new JwtTokenIssuer(secret, expirationMs);
        this.validator = new JwtTokenValidator(secret);
    }

    public String generateToken(UserDetails user) {
        String email = user instanceof AccountPrincipal account ? account.email() : null;
        String name = user instanceof AccountPrincipal account ? account.displayName() : null;
        return issuer.issue(UUID.fromString(user.getUsername()),
                user.getAuthorities().stream().map(GrantedAuthority::getAuthority).toList(), email, name);
    }

    public Optional<JwtClaims> validate(String token) {
        return validator.validate(token);
    }

    public JwtTokenValidator validator() {
        return validator;
    }

    public Instant getExpirationFromToken(String token) {
        return validator.validate(token).map(JwtClaims::expiresAt)
                .orElseThrow(() -> new IllegalArgumentException("Token inválido."));
    }

    public long getExpirationMs() {
        return issuer.expirationMs();
    }
}
