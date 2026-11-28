package com.fidiacorp.credicasa.identity.infrastructure.security;

import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Base64;
import java.util.Date;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** TC-SEC-01..04: emisión, firma válida, token expirado y token manipulado (ASR-SEC). */
class JwtTokenProviderTest {
    private static final String SECRET = "unit-test-only-hmac-signing-key-with-more-than-32-bytes";
    private final JwtTokenProvider provider = new JwtTokenProvider(SECRET, 60_000);
    private final String subject = UUID.randomUUID().toString();
    private final UserDetails user = new AccountPrincipal(subject, "unused", List.of(new SimpleGrantedAuthority("ROLE_CLIENT"),
            new SimpleGrantedAuthority("ROLE_REALTOR")), "ana@credicasa.pe", "Ana Pérez");

    @Test
    @DisplayName("TC-SEC-05: el JWT transporta correo y nombre visible para los demás microservicios")
    void carriesEmailAndDisplayNameClaims() {
        var claims = provider.validate(provider.generateToken(user)).orElseThrow();
        assertThat(claims.email()).isEqualTo("ana@credicasa.pe");
        assertThat(claims.displayName()).isEqualTo("Ana Pérez");
        var plain = new User(subject, "unused", List.of(new SimpleGrantedAuthority("ROLE_CLIENT")));
        assertThat(provider.validate(provider.generateToken(plain)).orElseThrow().email()).isNull();
    }

    @Test
    @DisplayName("TC-SEC-01: emite un JWT firmado con sujeto, roles, jti y expiración configurada")
    void issuesSignedTokenWithClaims() {
        Instant before = Instant.now();
        String token = provider.generateToken(user);
        assertThat(token.split("\\.")).hasSize(3);
        assertThat(provider.validate(token).orElseThrow().subject().toString()).isEqualTo(subject);
        assertThat(provider.validate(token).orElseThrow().roles()).containsExactly("ROLE_CLIENT", "ROLE_REALTOR");
        assertThat(provider.getExpirationFromToken(token)).isBetween(before.plusSeconds(58), before.plusSeconds(61));
        assertThat(provider.getExpirationMs()).isEqualTo(60_000);
        assertThat(provider.generateToken(user)).as("jti aleatorio: tokens distintos").isNotEqualTo(token);
    }

    @Test
    @DisplayName("TC-SEC-02: valida la firma HMAC-SHA256 de un token propio")
    void validatesOwnSignature() {
        assertThat(provider.validate(provider.generateToken(user))).isPresent();
    }

    @Test
    @DisplayName("TC-SEC-03: rechaza un token expirado")
    void rejectsExpiredToken() {
        String expired = Jwts.builder().subject(subject).issuedAt(Date.from(Instant.now().minusSeconds(120)))
                .expiration(Date.from(Instant.now().minusSeconds(60)))
                .signWith(Keys.hmacShaKeyFor(SECRET.getBytes(StandardCharsets.UTF_8))).compact();
        assertThat(provider.validate(expired)).isEmpty();
    }

    @Test
    @DisplayName("TC-SEC-04: rechaza tokens manipulados, firmados con otra clave, malformados o vacíos")
    void rejectsTamperedTokens() {
        String token = provider.generateToken(user);
        String[] parts = token.split("\\.");
        String forgedPayload = Base64.getUrlEncoder().withoutPadding().encodeToString(
                ("{\"sub\":\"" + UUID.randomUUID() + "\",\"roles\":[\"ROLE_REALTOR\"],\"exp\":4102444800}").getBytes(StandardCharsets.UTF_8));
        assertThat(provider.validate(parts[0] + "." + forgedPayload + "." + parts[2])).isEmpty();
        char last = parts[2].charAt(parts[2].length() - 2);
        String flippedSignature = parts[2].substring(0, parts[2].length() - 2) + (last == 'A' ? 'B' : 'A') + parts[2].charAt(parts[2].length() - 1);
        assertThat(provider.validate(parts[0] + "." + parts[1] + "." + flippedSignature)).isEmpty();
        JwtTokenProvider foreign = new JwtTokenProvider("another-unit-test-key-that-is-also-longer-than-32-bytes", 60_000);
        assertThat(provider.validate(foreign.generateToken(user))).isEmpty();
        String unsigned = Jwts.builder().subject(subject).expiration(Date.from(Instant.now().plusSeconds(60))).compact();
        assertThat(provider.validate(unsigned)).isEmpty();
        assertThat(provider.validate("not-a-jwt")).isEmpty();
        assertThat(provider.validate("")).isEmpty();
    }

    @Test
    @DisplayName("Configuración: rechaza expiraciones no positivas o mayores a 365 días")
    void rejectsInvalidExpirationConfiguration() {
        assertThatThrownBy(() -> new JwtTokenProvider(SECRET, 0)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new JwtTokenProvider(SECRET, 31_536_000_001L)).isInstanceOf(IllegalArgumentException.class);
        assertThat(new JwtTokenProvider(SECRET, 1).getExpirationMs()).isEqualTo(1);
        assertThatThrownBy(() -> provider.getExpirationFromToken("broken")).isInstanceOf(IllegalArgumentException.class);
    }
}
