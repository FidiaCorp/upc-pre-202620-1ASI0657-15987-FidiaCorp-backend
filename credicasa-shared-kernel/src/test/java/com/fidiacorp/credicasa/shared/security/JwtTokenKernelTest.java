package com.fidiacorp.credicasa.shared.security;

import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Date;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class JwtTokenKernelTest {
    static final String SECRET = "test-secret-key-for-credicasa-shared-kernel-0123456789";
    final JwtTokenIssuer issuer = new JwtTokenIssuer(SECRET, 3_600_000);
    final JwtTokenValidator validator = new JwtTokenValidator(SECRET);

    @Test
    void issuedTokenRoundTripsAllClaims() {
        UUID id = UUID.randomUUID();
        String token = issuer.issue(id, List.of("ROLE_CLIENT"), "ana@credicasa.pe", "Ana Pérez");
        JwtClaims claims = validator.validate(token).orElseThrow();
        assertThat(claims.subject()).isEqualTo(id);
        assertThat(claims.roles()).containsExactly("ROLE_CLIENT");
        assertThat(claims.hasRole("CLIENT")).isTrue();
        assertThat(claims.hasRole("ROLE_CLIENT")).isTrue();
        assertThat(claims.hasRole("REALTOR")).isFalse();
        assertThat(claims.tokenId()).isNotBlank();
        assertThat(claims.email()).isEqualTo("ana@credicasa.pe");
        assertThat(claims.displayName()).isEqualTo("Ana Pérez");
        assertThat(claims.expiresAt()).isAfter(claims.issuedAt());
        assertThat(issuer.expirationMs()).isEqualTo(3_600_000);
    }

    @Test
    void optionalClaimsMayBeAbsent() {
        String token = issuer.issue(UUID.randomUUID(), List.of(), null, null);
        JwtClaims claims = validator.validate(token).orElseThrow();
        assertThat(claims.email()).isNull();
        assertThat(claims.displayName()).isNull();
        assertThat(claims.roles()).isEmpty();
    }

    @Test
    void rejectsForgedExpiredMalformedAndForeignTokens() {
        String other = new JwtTokenIssuer("another-secret-key-that-is-long-enough-1234567890", 60_000)
                .issue(UUID.randomUUID(), List.of("ROLE_CLIENT"), null, null);
        assertThat(validator.validate(other)).isEmpty();
        Clock past = Clock.fixed(Instant.parse("2020-01-01T00:00:00Z"), ZoneOffset.UTC);
        String expired = new JwtTokenIssuer(SECRET, 1_000, past).issue(UUID.randomUUID(), List.of("ROLE_CLIENT"), null, null);
        assertThat(validator.validate(expired)).isEmpty();
        assertThat(validator.validate("not-a-jwt")).isEmpty();
        assertThat(validator.validate(null)).isEmpty();
        assertThat(validator.validate("  ")).isEmpty();
        var key = Keys.hmacShaKeyFor(SECRET.getBytes(StandardCharsets.UTF_8));
        String notUuid = Jwts.builder().subject("admin").expiration(new Date(System.currentTimeMillis() + 60_000)).signWith(key).compact();
        assertThat(validator.validate(notUuid)).isEmpty();
        String noSubject = Jwts.builder().expiration(new Date(System.currentTimeMillis() + 60_000)).signWith(key).compact();
        assertThat(validator.validate(noSubject)).isEmpty();
    }

    @Test
    void nonListRolesClaimYieldsNoAuthorities() {
        var key = Keys.hmacShaKeyFor(SECRET.getBytes(StandardCharsets.UTF_8));
        String token = Jwts.builder().subject(UUID.randomUUID().toString()).claim("roles", "ROLE_ADMIN")
                .expiration(new Date(System.currentTimeMillis() + 60_000)).signWith(key).compact();
        JwtClaims claims = validator.validate(token).orElseThrow();
        assertThat(claims.roles()).isEmpty();
        assertThat(claims.issuedAt()).isNull();
    }

    @Test
    void rejectsWeakSecretsAndUnsafeExpirations() {
        assertThatThrownBy(() -> new JwtTokenValidator("short")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new JwtTokenValidator(null)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new JwtTokenIssuer(SECRET, 0)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new JwtTokenIssuer(SECRET, JwtTokenIssuer.MAX_EXPIRATION_MS + 1)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new JwtClaims(null, List.of(), null, null, null, null, null)).isInstanceOf(NullPointerException.class);
        assertThat(new JwtClaims(UUID.randomUUID(), null, null, null, null, null, null).roles()).isEmpty();
    }
}
