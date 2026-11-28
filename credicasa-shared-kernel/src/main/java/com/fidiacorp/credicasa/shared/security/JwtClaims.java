package com.fidiacorp.credicasa.shared.security;

import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/** Claims validados de un JWT de CrediCasa. {@code subject} es el UUID de la cuenta. */
public record JwtClaims(UUID subject, List<String> roles, String tokenId, Instant issuedAt, Instant expiresAt,
                        String email, String displayName) {
    public JwtClaims {
        Objects.requireNonNull(subject, "subject must not be null");
        roles = roles == null ? List.of() : List.copyOf(roles);
    }

    public boolean hasRole(String role) {
        String expected = role.startsWith("ROLE_") ? role : "ROLE_" + role;
        return roles.contains(expected);
    }
}
