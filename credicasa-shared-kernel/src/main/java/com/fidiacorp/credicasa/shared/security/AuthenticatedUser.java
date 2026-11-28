package com.fidiacorp.credicasa.shared.security;

import org.springframework.security.core.Authentication;

import java.security.Principal;
import java.util.Optional;
import java.util.UUID;

/** Acceso tipado al principal autenticado por {@link JwtAuthenticationFilter}. */
public final class AuthenticatedUser {
    private AuthenticatedUser() {
    }

    public static UUID id(Principal principal) {
        return UUID.fromString(principal.getName());
    }

    public static Optional<JwtClaims> claims(Principal principal) {
        if (principal instanceof Authentication authentication && authentication.getDetails() instanceof JwtClaims claims) {
            return Optional.of(claims);
        }
        return Optional.empty();
    }

    /** Nombre visible del JWT o, en su defecto, el correo; nunca nulo. */
    public static String displayName(Principal principal) {
        return claims(principal).map(c -> c.displayName() != null ? c.displayName() : c.email())
                .filter(name -> name != null && !name.isBlank()).orElse("Usuario CrediCasa");
    }

    public static Optional<String> email(Principal principal) {
        return claims(principal).map(JwtClaims::email);
    }
}
