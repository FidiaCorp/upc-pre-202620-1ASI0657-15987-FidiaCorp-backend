package com.fidiacorp.credicasa.shared.security;

import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.util.Collection;
import java.util.Date;
import java.util.List;
import java.util.UUID;

/** Emite JWT HS256 (solo identity-subscription-service). Incluye roles, jti, email y nombre visible. */
public class JwtTokenIssuer {
    public static final long MAX_EXPIRATION_MS = 31_536_000_000L;
    private final SecretKey key;
    private final long expirationMs;
    private final Clock clock;

    public JwtTokenIssuer(String secret, long expirationMs) {
        this(secret, expirationMs, Clock.systemUTC());
    }

    public JwtTokenIssuer(String secret, long expirationMs, Clock clock) {
        this.key = JwtTokenValidator.signingKey(secret);
        if (expirationMs <= 0 || expirationMs > MAX_EXPIRATION_MS) {
            throw new IllegalArgumentException("JWT expiration must be between 1 ms and 365 days");
        }
        this.expirationMs = expirationMs;
        this.clock = clock;
    }

    public String issue(UUID subject, Collection<String> roles, String email, String displayName) {
        Instant now = clock.instant();
        var builder = Jwts.builder()
                .subject(subject.toString())
                .id(UUID.randomUUID().toString())
                .claim("roles", List.copyOf(roles))
                .issuedAt(Date.from(now))
                .expiration(new Date(Math.addExact(now.toEpochMilli(), expirationMs)));
        if (email != null) builder.claim("email", email);
        if (displayName != null) builder.claim("name", displayName);
        return builder.signWith(key).compact();
    }

    public long expirationMs() {
        return expirationMs;
    }

    static SecretKey keyFor(String secret) {
        return Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
    }
}
