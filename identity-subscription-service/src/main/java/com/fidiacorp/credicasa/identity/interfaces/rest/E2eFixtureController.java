package com.fidiacorp.credicasa.identity.interfaces.rest;

import com.fidiacorp.credicasa.identity.application.dto.response.AccountSummaryResponse;
import com.fidiacorp.credicasa.identity.application.usecase.AccountDirectoryService;
import com.fidiacorp.credicasa.shared.error.ResourceNotFoundException;
import io.swagger.v3.oas.annotations.Hidden;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.UUID;

/**
 * Fixture de aceptación E2E: promueve una cuenta a REALTOR. Solo existe si {@code APP_E2E_FIXTURE_TOKEN} está definido
 * (nunca en producción), exige {@code X-Fixture-Token} y vive bajo /api/v1/internal (no expuesto por el gateway).
 */
@Hidden
@RestController
@RequestMapping("/api/v1/internal/e2e")
@ConditionalOnExpression("!'${app.e2e.fixture-token:}'.isBlank()")
public class E2eFixtureController {
    private static final Logger log = LoggerFactory.getLogger(E2eFixtureController.class);
    private final AccountDirectoryService directory;
    private final byte[] token;

    public E2eFixtureController(AccountDirectoryService directory, @Value("${app.e2e.fixture-token}") String token) {
        this.directory = directory;
        this.token = token.getBytes(StandardCharsets.UTF_8);
    }

    @PostMapping("/accounts/{id}/realtor")
    public AccountSummaryResponse promote(@PathVariable UUID id, @RequestHeader(value = "X-Fixture-Token", required = false) String presented) {
        if (presented == null || !MessageDigest.isEqual(token, presented.getBytes(StandardCharsets.UTF_8))) {
            throw new ResourceNotFoundException("Recurso no disponible.");
        }
        log.warn("[SECURITY-AUDIT] Fixture E2E: cuenta {} promovida a ROLE_REALTOR", id);
        return directory.grantRealtor(id).orElseThrow(() -> new ResourceNotFoundException("Cuenta no encontrada."));
    }
}
