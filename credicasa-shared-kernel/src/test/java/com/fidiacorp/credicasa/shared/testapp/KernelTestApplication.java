package com.fidiacorp.credicasa.shared.testapp;

import com.fidiacorp.credicasa.shared.error.BusinessRuleViolationException;
import com.fidiacorp.credicasa.shared.error.ResourceNotFoundException;
import com.fidiacorp.credicasa.shared.error.UpstreamServiceException;
import com.fidiacorp.credicasa.shared.security.JwtAuthenticationFilter;
import com.fidiacorp.credicasa.shared.security.JwtTokenValidator;
import com.fidiacorp.credicasa.shared.security.StatelessSecuritySupport;
import com.fidiacorp.credicasa.shared.web.CorrelationIdFilter;
import com.fidiacorp.credicasa.shared.web.ProblemDetailExceptionHandler;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.server.ResponseStatusException;

import java.security.Principal;
import java.time.DateTimeException;
import java.util.UUID;

@SpringBootApplication
public class KernelTestApplication {
    public static final String SECRET = "test-secret-key-for-credicasa-shared-kernel-0123456789";

    @Bean
    JwtAuthenticationFilter jwtAuthenticationFilter() {
        return new JwtAuthenticationFilter(new JwtTokenValidator(SECRET), null);
    }

    @Bean
    FilterRegistrationBean<JwtAuthenticationFilter> jwtRegistration(JwtAuthenticationFilter filter) {
        FilterRegistrationBean<JwtAuthenticationFilter> registration = new FilterRegistrationBean<>(filter);
        registration.setEnabled(false);
        return registration;
    }

    @Bean
    CorrelationIdFilter correlationIdFilter() {
        return new CorrelationIdFilter();
    }

    @Bean
    SecurityFilterChain chain(HttpSecurity http, JwtAuthenticationFilter filter) throws Exception {
        return StatelessSecuritySupport.apply(http, filter)
                .authorizeHttpRequests(auth -> auth.requestMatchers("/t/**").permitAll().anyRequest().authenticated())
                .build();
    }

    @RestControllerAdvice
    static class Handler extends ProblemDetailExceptionHandler {
    }

    record Body(@NotBlank String name) {}

    @RestController
    @Validated
    @RequestMapping("/t")
    static class ThrowingController {
        @GetMapping("/{kind}")
        String fail(@PathVariable String kind) {
            switch (kind) {
                case "illegal-arg" -> throw new IllegalArgumentException("bad arg");
                case "illegal-state" -> throw new IllegalStateException("conflict");
                case "bad-creds" -> throw new BadCredentialsException("x");
                case "denied" -> throw new AccessDeniedException("x");
                case "security" -> throw new SecurityException("x");
                case "not-found" -> throw new ResourceNotFoundException("missing");
                case "rule" -> throw new BusinessRuleViolationException("rule broken");
                case "upstream" -> throw new UpstreamServiceException("pricing down", new RuntimeException());
                case "optimistic" -> throw new OptimisticLockingFailureException("x");
                case "datetime" -> throw new DateTimeException("x");
                case "framework-404" -> throw new ResponseStatusException(HttpStatus.NOT_FOUND);
                case "framework-409" -> throw new ResponseStatusException(HttpStatus.CONFLICT);
                case "framework-503" -> throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE);
                case "boom" -> throw new RuntimeException("secret detail");
                default -> {
                    return "ok";
                }
            }
        }

        @GetMapping("/typed/{id}")
        String typed(@PathVariable UUID id) {
            return id.toString();
        }

        @GetMapping("/query")
        String query(@RequestParam @Min(1) int size) {
            return Integer.toString(size);
        }

        @PostMapping("/body")
        String body(@Valid @RequestBody Body body) {
            return body.name();
        }
    }

    @RestController
    static class SecuredController {
        @GetMapping("/secure/me")
        String me(Principal principal) {
            return principal.getName();
        }
    }
}
