package com.fidiacorp.credicasa.shared.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Validación stateless de JWT HS256 (firma, expiración, sujeto UUID y roles) para todos los microservicios.
 * La revocación es opcional y la decide cada consumidor ({@link RevocationChecker}).
 */
public class JwtTokenValidator {
    private static final Logger log = LoggerFactory.getLogger(JwtTokenValidator.class);
    public static final int MIN_SECRET_BYTES = 32;
    private final SecretKey key;

    public JwtTokenValidator(String secret) {
        this.key = signingKey(secret);
    }

    static SecretKey signingKey(String secret) {
        if (secret == null || secret.getBytes(StandardCharsets.UTF_8).length < MIN_SECRET_BYTES) {
            throw new IllegalArgumentException("JWT secret must have at least 32 bytes");
        }
        return JwtTokenIssuer.keyFor(secret);
    }

    public Optional<JwtClaims> validate(String token) {
        if (token == null || token.isBlank()) {
            return Optional.empty();
        }
        try {
            Claims claims = Jwts.parser().verifyWith(key).build().parseSignedClaims(token).getPayload();
            UUID subject = UUID.fromString(claims.getSubject());
            Object rawRoles = claims.get("roles");
            List<String> roles = rawRoles instanceof List<?> list ? list.stream().map(String::valueOf).toList() : List.of();
            return Optional.of(new JwtClaims(subject, roles, claims.getId(),
                    claims.getIssuedAt() == null ? null : claims.getIssuedAt().toInstant(),
                    claims.getExpiration().toInstant(), claims.get("email", String.class), claims.get("name", String.class)));
        } catch (JwtException | IllegalArgumentException | NullPointerException e) {
            log.debug("JWT rechazado: {}", e.getMessage());
            return Optional.empty();
        }
    }
}
