package com.fidiacorp.credicasa.shared.security;

import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

/** Configuración común de cadenas stateless: sin CSRF/CORS propios (los centraliza el gateway), 401/403 sin cuerpo. */
public final class StatelessSecuritySupport {
    public static final String[] PUBLIC_DOCS = {"/v3/api-docs/**", "/swagger-ui/**", "/swagger-ui.html", "/actuator/health/**",
            "/actuator/health", "/actuator/info"};

    private StatelessSecuritySupport() {
    }

    public static HttpSecurity apply(HttpSecurity http, JwtAuthenticationFilter filter) throws Exception {
        return http
                .csrf(AbstractHttpConfigurer::disable)
                .cors(AbstractHttpConfigurer::disable)
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .exceptionHandling(errors -> errors
                        .authenticationEntryPoint((request, response, exception) -> response.sendError(401))
                        .accessDeniedHandler((request, response, exception) -> response.sendError(403)))
                .addFilterBefore(filter, UsernamePasswordAuthenticationFilter.class);
    }
}
