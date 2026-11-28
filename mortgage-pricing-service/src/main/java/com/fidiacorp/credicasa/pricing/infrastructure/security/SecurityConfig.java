package com.fidiacorp.credicasa.pricing.infrastructure.security;

import com.fidiacorp.credicasa.shared.security.JwtAuthenticationFilter;
import com.fidiacorp.credicasa.shared.security.JwtTokenValidator;
import com.fidiacorp.credicasa.shared.security.RevocationChecker;
import com.fidiacorp.credicasa.shared.security.StatelessSecuritySupport;
import com.fidiacorp.credicasa.shared.web.CorrelationIdFilter;
import com.fidiacorp.credicasa.shared.web.InternalApiGuardFilter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.web.SecurityFilterChain;

/**
 * Driver 1: Seguridad (ASR-SEC). Resource server stateless: valida el JWT con la librería compartida (firma HS256,
 * expiración, sujeto UUID y roles). La revocación se verifica en el API Gateway contra identity-subscription-service;
 * CORS también se centraliza en el gateway. La autorización por recurso se decide en los casos de uso (anti-BOLA).
 */
@Configuration
@EnableWebSecurity
@EnableMethodSecurity
public class SecurityConfig {
    @Bean
    public JwtTokenValidator jwtTokenValidator(@Value("${app.security.jwt.secret}") String secret) {
        return new JwtTokenValidator(secret);
    }

    @Bean
    public JwtAuthenticationFilter jwtAuthenticationFilter(JwtTokenValidator validator) {
        return new JwtAuthenticationFilter(validator, RevocationChecker.NONE);
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
                        .requestMatchers(StatelessSecuritySupport.PUBLIC_DOCS).permitAll()
                        .requestMatchers("/api/v1/banking/benchmarks", "/actuator/circuitbreakers", "/actuator/circuitbreakers/**", "/actuator/circuitbreakerevents", "/actuator/circuitbreakerevents/**").permitAll()
                        .anyRequest().authenticated())
                .build();
    }
}
