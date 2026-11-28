package com.fidiacorp.credicasa.identity.infrastructure.bootstrap;

import com.fidiacorp.credicasa.identity.application.dto.response.AccountSummaryResponse;
import com.fidiacorp.credicasa.identity.application.usecase.AccountDirectoryService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.List;
import java.util.Optional;

/**
 * Alta controlada de asesores (ROLE_REALTOR) en despliegues sin fixture E2E, por ejemplo Azure Container Apps.
 * <p>
 * El rol no se puede asignar por la API pública. El operador registra la cuenta desde la aplicación y luego define
 * {@code REALTOR_BOOTSTRAP_EMAILS} (correos separados por comas) en identity-subscription-service; al iniciar una nueva
 * revisión, cada cuenta existente con esos correos se promueve a REALTOR. Es idempotente y deja traza de auditoría.
 */
@Component
@ConditionalOnExpression("!'${app.bootstrap.realtor-emails:}'.isBlank()")
public class RealtorBootstrapRunner implements ApplicationRunner {
    private static final Logger log = LoggerFactory.getLogger(RealtorBootstrapRunner.class);
    private final AccountDirectoryService directory;
    private final List<String> emails;

    public RealtorBootstrapRunner(AccountDirectoryService directory, @Value("${app.bootstrap.realtor-emails}") String emails) {
        this.directory = directory;
        this.emails = Arrays.stream(emails.split(",")).map(String::strip).filter(e -> !e.isEmpty()).distinct().toList();
    }

    @Override
    public void run(ApplicationArguments args) {
        for (String email : emails) {
            Optional<AccountSummaryResponse> promoted = directory.grantRealtorByEmail(email);
            if (promoted.isPresent()) {
                log.warn("[SECURITY-AUDIT] Bootstrap: cuenta {} promovida a ROLE_REALTOR", promoted.get().id());
            } else {
                log.warn("[SECURITY-AUDIT] Bootstrap: no existe una cuenta registrada con el correo indicado; regístrela y reinicie el servicio");
            }
        }
    }

    List<String> configuredEmails() {
        return emails;
    }
}
