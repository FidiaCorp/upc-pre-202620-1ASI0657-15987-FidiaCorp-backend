package com.fidiacorp.credicasa.identity.testing;

import com.fidiacorp.credicasa.identity.infrastructure.security.AccountPrincipal;
import com.fidiacorp.credicasa.shared.security.JwtTokenIssuer;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.UUID;

/** Utilidades de prueba para fabricar JWT con la misma librería compartida que usa producción. */
public final class TestTokens {
    private TestTokens() {
    }

    /** JWT correctamente firmado pero emitido en 2020 y expirado hace años. */
    public static String expired(String secret, UserDetails user) {
        JwtTokenIssuer issuer = new JwtTokenIssuer(secret, 1_000, Clock.fixed(Instant.parse("2020-01-01T00:00:00Z"), ZoneOffset.UTC));
        return issuer.issue(UUID.fromString(user.getUsername()), user.getAuthorities().stream().map(GrantedAuthority::getAuthority).toList(),
                user instanceof AccountPrincipal p ? p.email() : null, user instanceof AccountPrincipal p ? p.displayName() : null);
    }
}
