package com.fidiacorp.credicasa.identity.infrastructure.security;

import com.fidiacorp.credicasa.identity.application.usecase.TokenRevocationService;
import com.fidiacorp.credicasa.identity.domain.ports.out.AccountRepositoryPort;
import com.fidiacorp.credicasa.shared.security.JwtAuthenticationFilter;
import com.fidiacorp.credicasa.shared.security.StatelessSecuritySupport;
import com.fidiacorp.credicasa.shared.web.CorrelationIdFilter;
import com.fidiacorp.credicasa.shared.web.InternalApiGuardFilter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;

/**
 * Driver 1: Seguridad (ASR-SEC). Cadena stateless con la librería compartida: JWT del propio emisor, rechazo de tokens
 * revocados (SHA-256 en identity_db) o de cuentas inexistentes. CORS se centraliza en el API Gateway.
 */
@Configuration
@EnableWebSecurity
@EnableMethodSecurity
public class SecurityConfig {
    @Bean
    public static PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    public AuthenticationManager authenticationManager(AuthenticationConfiguration configuration) throws Exception {
        return configuration.getAuthenticationManager();
    }

    @Bean
    public JwtAuthenticationFilter jwtAuthenticationFilter(JwtTokenProvider tokens, TokenRevocationService revocations,
                                                           AccountRepositoryPort accounts) {
        return new JwtAuthenticationFilter(tokens.validator(), token -> revocations.isRevoked(token)
                || tokens.validate(token).map(claims -> accounts.findById(claims.subject()).isEmpty()).orElse(true));
    }

    @Bean
    public FilterRegistrationBean<JwtAuthenticationFilter> jwtFilterRegistration(JwtAuthenticationFilter filter) {
        FilterRegistrationBean<JwtAuthenticationFilter> registration = new FilterRegistrationBean<>(filter);
        registration.setEnabled(false);
        return registration;
    }

    @Bean
    public FilterRegistrationBean<CorrelationIdFilter> correlationIdFilter() {
        FilterRegistrationBean<CorrelationIdFilter> registration = new FilterRegistrationBean<>(new CorrelationIdFilter());
        registration.setOrder(Ordered.HIGHEST_PRECEDENCE);
        return registration;
    }

    @Bean
    public FilterRegistrationBean<InternalApiGuardFilter> internalApiGuard(@Value("${app.internal.token:}") String token) {
        FilterRegistrationBean<InternalApiGuardFilter> registration = new FilterRegistrationBean<>(new InternalApiGuardFilter(token));
        registration.setOrder(Ordered.HIGHEST_PRECEDENCE + 1);
        return registration;
    }

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http, JwtAuthenticationFilter jwtFilter) throws Exception {
        return StatelessSecuritySupport.apply(http, jwtFilter)
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/api/v1/auth/logout").authenticated()
                        .requestMatchers("/api/v1/auth/**").permitAll()
                        .requestMatchers(StatelessSecuritySupport.PUBLIC_DOCS).permitAll()
                        // Introspección para el API Gateway y fixture E2E (protegido por su propio secreto).
                        .requestMatchers("/api/v1/internal/tokens/status", "/api/v1/internal/e2e/**").permitAll()
                        .anyRequest().authenticated())
                .build();
    }
}
