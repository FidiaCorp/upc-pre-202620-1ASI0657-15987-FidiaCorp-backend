package com.fidiacorp.credicasa.identity.interfaces.rest;

import com.fidiacorp.credicasa.identity.application.dto.response.AccountSummaryResponse;
import com.fidiacorp.credicasa.identity.application.usecase.AccountDirectoryService;
import com.fidiacorp.credicasa.identity.application.usecase.TokenRevocationService;
import com.fidiacorp.credicasa.identity.infrastructure.security.JwtTokenProvider;
import com.fidiacorp.credicasa.shared.error.ResourceNotFoundException;
import com.fidiacorp.credicasa.shared.security.JwtAuthenticationFilter;
import io.swagger.v3.oas.annotations.Hidden;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/**
 * API interna servicio-a-servicio (nunca enrutada por el API Gateway; protegida además con X-Internal-Token).
 * Sustituye las FK físicas hacia {@code accounts} de los demás contextos.
 */
@Hidden
@RestController
@RequestMapping("/api/v1/internal")
public class InternalAccountController {
    private final AccountDirectoryService directory;
    private final TokenRevocationService revocations;
    private final JwtTokenProvider tokens;

    public InternalAccountController(AccountDirectoryService directory, TokenRevocationService revocations, JwtTokenProvider tokens) {
        this.directory = directory;
        this.revocations = revocations;
        this.tokens = tokens;
    }

    @GetMapping("/accounts/{id}")
    public AccountSummaryResponse account(@PathVariable UUID id) {
        return directory.summary(id).orElseThrow(() -> new ResourceNotFoundException("Cuenta no encontrada."));
    }

    @GetMapping("/accounts/{id}/exists")
    public Map<String, Object> exists(@PathVariable UUID id) {
        return Map.of("id", id, "exists", directory.exists(id));
    }

    @GetMapping("/accounts/lookup")
    public AccountSummaryResponse lookup(@RequestParam String email) {
        return directory.findByEmail(email).orElseThrow(() -> new ResourceNotFoundException("Cuenta no encontrada."));
    }

    /** Introspección para el API Gateway: el token es válido, no revocado y su cuenta existe. */
    @GetMapping("/tokens/status")
    public ResponseEntity<Map<String, Object>> tokenStatus(@RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authorization) {
        String token = JwtAuthenticationFilter.bearerToken(authorization);
        Map<String, Object> body = new LinkedHashMap<>();
        var claims = token == null ? java.util.Optional.<com.fidiacorp.credicasa.shared.security.JwtClaims>empty() : tokens.validate(token);
        boolean active = claims.isPresent() && !revocations.isRevoked(token) && directory.exists(claims.get().subject());
        body.put("active", active);
        claims.filter(c -> active).ifPresent(c -> body.put("subject", c.subject()));
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(body);
    }
}
