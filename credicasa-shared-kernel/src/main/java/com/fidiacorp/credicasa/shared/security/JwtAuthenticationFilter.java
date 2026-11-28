package com.fidiacorp.credicasa.shared.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * Filtro servlet stateless: Bearer válido (y no revocado) → Authentication cuyo nombre es el UUID de la cuenta y cuyos
 * detalles son los {@link JwtClaims}. Un token inválido deja la petición anónima (401 en rutas protegidas).
 */
public class JwtAuthenticationFilter extends OncePerRequestFilter {
    public static final String BEARER = "Bearer ";
    private final JwtTokenValidator validator;
    private final RevocationChecker revocations;

    public JwtAuthenticationFilter(JwtTokenValidator validator, RevocationChecker revocations) {
        this.validator = validator;
        this.revocations = revocations == null ? RevocationChecker.NONE : revocations;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String token = bearerToken(request.getHeader("Authorization"));
        if (token != null) {
            validator.validate(token).filter(claims -> !revocations.isRevoked(token)).ifPresent(claims -> {
                var authentication = new UsernamePasswordAuthenticationToken(claims.subject().toString(), null,
                        claims.roles().stream().map(SimpleGrantedAuthority::new).toList());
                authentication.setDetails(claims);
                SecurityContextHolder.getContext().setAuthentication(authentication);
            });
        }
        chain.doFilter(request, response);
    }

    public static String bearerToken(String header) {
        if (header == null || !header.startsWith(BEARER) || header.length() == BEARER.length()) {
            return null;
        }
        return header.substring(BEARER.length()).trim();
    }
}
