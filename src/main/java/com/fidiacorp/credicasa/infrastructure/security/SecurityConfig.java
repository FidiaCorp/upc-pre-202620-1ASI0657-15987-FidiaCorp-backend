package com.fidiacorp.credicasa.infrastructure.security;

import org.springframework.context.annotation.Bean;
import org.springframework.beans.factory.annotation.Value;
import java.net.URI;
import java.util.Arrays;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.List;

/**
 * Driver 1: Seguridad (ASR-SEC) - Arquitectura de Seguridad Spring Security 6
 * - Sesiones sin estado (STATELESS).
 * - Autenticación basada en JSON Web Tokens (JWT).
 * - Control de roles RBAC (ROLE_CLIENT, ROLE_REALTOR).
 * - Encriptación de contraseñas con BCrypt.
 */
@Configuration
@EnableWebSecurity
@EnableMethodSecurity
public class SecurityConfig {

    private final JwtAuthFilter jwtAuthFilter;

    private final List<String> allowedOrigins;

    public SecurityConfig(JwtAuthFilter jwtAuthFilter,
            @Value("${app.security.allowed-origins}") String origins) {
        this.jwtAuthFilter = jwtAuthFilter;
        this.allowedOrigins = Arrays.stream(origins.split(","))
                .map(String::trim).filter(value -> !value.isEmpty()).toList();
        for (String origin : allowedOrigins) {
            if (origin.equals("http://127.0.0.1:[*]") || origin.equals("http://localhost:[*]")) continue;
            URI uri = URI.create(origin);
            if (origin.contains("*") || !("http".equals(uri.getScheme()) || "https".equals(uri.getScheme()))
                    || uri.getHost() == null || uri.getUserInfo() != null
                    || (uri.getPath() != null && !uri.getPath().isEmpty())
                    || uri.getQuery() != null || uri.getFragment() != null) {
                throw new IllegalArgumentException("CORS requires explicit HTTP(S) origins");
            }
        }
    }

    @Bean
    public static PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    public AuthenticationManager authenticationManager(AuthenticationConfiguration configuration) throws Exception {
        return configuration.getAuthenticationManager();
    }

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
                .exceptionHandling(errors -> errors
                    .authenticationEntryPoint((request, response, exception) -> response.sendError(401))
                    .accessDeniedHandler((request, response, exception) -> response.sendError(403)))
                .csrf(AbstractHttpConfigurer::disable)
                .cors(cors -> cors.configurationSource(corsConfigurationSource()))
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        // Logout must be authenticated before the public auth wildcard.
                        .requestMatchers("/api/v1/auth/logout").authenticated()
                        // Rutas públicas: Autenticación, Documentación Swagger OpenAPI
                        .requestMatchers(
                                "/api/v1/auth/**",
                                "/v3/api-docs/**",
                                "/swagger-ui/**",
                                "/swagger-ui.html",
                                "/actuator/health"
                        ).permitAll()
                        // Benchmarks públicos de tasas del sistema financiero SBS
                        .requestMatchers("/api/v1/banking/benchmarks").permitAll()
                        // Ofertas bancarias y simulaciones de crédito restringidas a CLIENT o REALTOR
                        .requestMatchers("/api/v1/banking/banks/**").hasAnyRole("CLIENT", "REALTOR")
                        .requestMatchers("/api/v1/simulations/**").hasAnyRole("CLIENT", "REALTOR")
                        // Cualquier otra petición requiere autenticación
                        .anyRequest().authenticated()
                )
                .addFilterBefore(jwtAuthFilter, UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }

    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration config = new CorsConfiguration();
        config.setAllowedOriginPatterns(allowedOrigins);
        config.setAllowedMethods(List.of("GET", "POST", "PUT", "DELETE", "PATCH", "OPTIONS"));
        config.setAllowedHeaders(List.of("Authorization", "Content-Type", "Accept", "X-Requested-With"));
        config.setExposedHeaders(List.of("Authorization", "Retry-After"));
        config.setAllowCredentials(false);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", config);
        return source;
    }
}
