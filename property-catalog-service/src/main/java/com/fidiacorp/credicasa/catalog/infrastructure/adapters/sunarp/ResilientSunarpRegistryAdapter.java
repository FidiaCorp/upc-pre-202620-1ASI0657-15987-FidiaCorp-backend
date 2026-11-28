package com.fidiacorp.credicasa.catalog.infrastructure.adapters.sunarp;

import com.fidiacorp.credicasa.catalog.domain.model.PropertyLegitimacyReport;
import com.fidiacorp.credicasa.catalog.domain.model.PropertyLegitimacyStatus;
import com.fidiacorp.credicasa.catalog.domain.ports.out.SunarpRegistryVerificationPort;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

/**
 * US09 + QA-02: decorador Resilience4j del registro SUNARP. Si la consulta registral falla de forma repetida el
 * circuito {@code sunarpRegistry} se abre y la verificación degrada a {@code PENDING_VERIFICATION} (sin inventar un
 * resultado positivo), sellado igualmente con SHA-256 para que el certificado sea verificable.
 */
@Primary
@Component
public class ResilientSunarpRegistryAdapter implements SunarpRegistryVerificationPort {
    private static final Logger log = LoggerFactory.getLogger(ResilientSunarpRegistryAdapter.class);
    public static final String FALLBACK_NOTES = "Registro SUNARP temporalmente no disponible: la verificación queda pendiente y puede reintentarse.";
    private final SunarpRegistryAdapter delegate;
    private final Clock clock;

    public ResilientSunarpRegistryAdapter(SunarpRegistryAdapter delegate, Clock clock) {
        this.delegate = delegate;
        this.clock = clock;
    }

    @Override
    @CircuitBreaker(name = "sunarpRegistry", fallbackMethod = "pendingVerification")
    public PropertyLegitimacyReport verifyRegistryNumber(UUID propertyId, String registryNumber, String claimedOwner) {
        return delegate.verifyRegistryNumber(propertyId, registryNumber, claimedOwner);
    }

    PropertyLegitimacyReport pendingVerification(UUID propertyId, String registryNumber, String claimedOwner, Throwable cause) {
        log.warn("[RESILIENCE] Circuito sunarpRegistry degradado para el inmueble {}: {}", propertyId, cause.toString());
        String number = registryNumber == null ? "" : registryNumber.trim();
        String owner = claimedOwner != null && !claimedOwner.isBlank() ? claimedOwner.trim() : "Titular no declarado";
        Instant at = clock.instant().truncatedTo(ChronoUnit.MILLIS);
        PropertyLegitimacyStatus status = PropertyLegitimacyStatus.PENDING_VERIFICATION;
        return new PropertyLegitimacyReport(propertyId, number, status, PropertyLegitimacyReport.DEFAULT_OFFICE, owner, at,
                FALLBACK_NOTES, SunarpRegistryAdapter.certificateHash(propertyId, number, owner, status, at));
    }
}
