package com.fidiacorp.credicasa.catalog.infrastructure.adapters.sunarp;

import com.fidiacorp.credicasa.catalog.domain.model.PropertyLegitimacyReport;
import com.fidiacorp.credicasa.catalog.domain.model.PropertyLegitimacyStatus;
import com.fidiacorp.credicasa.catalog.domain.ports.out.SunarpRegistryVerificationPort;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.HexFormat;
import java.util.UUID;
import java.util.regex.Pattern;

/**
 * Adaptador SUNARP (US09). La consulta registral es simulada (DEMO) — no hay API pública de SUNARP —, pero el reporte
 * se sella con un hash SHA-256 real sobre {@code propertyId|registryNumber|claimedOwner|status|verifiedAt}.
 */
@Component
public class SunarpRegistryAdapter implements SunarpRegistryVerificationPort {

    private static final Pattern VALID_PARTIDA_PATTERN = Pattern.compile("^(PE-)?\\d{8}$");
    private static final String OFFICE = PropertyLegitimacyReport.DEFAULT_OFFICE;
    private final Clock clock;

    public SunarpRegistryAdapter(Clock clock) {
        this.clock = clock;
    }

    @Override
    public PropertyLegitimacyReport verifyRegistryNumber(UUID propertyId, String registryNumber, String claimedOwner) {
        String number = registryNumber != null ? registryNumber.trim() : "";
        String owner = claimedOwner != null && !claimedOwner.isBlank() ? claimedOwner.trim() : "Titular no declarado";
        Instant verifiedAt = clock.instant().truncatedTo(ChronoUnit.MILLIS);

        PropertyLegitimacyStatus status;
        String notes;
        if (!VALID_PARTIDA_PATTERN.matcher(number).matches()) {
            status = PropertyLegitimacyStatus.FLAGGED_ANOMALY;
            notes = "Partida registral no cumple con el formato oficial de 8 dígitos de la Sunarp.";
        } else if (number.endsWith("00000000") || number.endsWith("9999")) {
            status = PropertyLegitimacyStatus.FLAGGED_ANOMALY;
            notes = "Observación registral: Título duplicado o con carga hipotecaria no saneada.";
        } else {
            status = PropertyLegitimacyStatus.VERIFIED_GENUINE;
            notes = "Partida electrónica verificada en el Registro de Predios (consulta DEMO). Inmueble libre de gravámenes no informados.";
        }
        return new PropertyLegitimacyReport(propertyId, number, status, OFFICE, owner, verifiedAt, notes,
                certificateHash(propertyId, number, owner, status, verifiedAt));
    }

    public static String certificateHash(UUID propertyId, String registryNumber, String claimedOwner,
                                         PropertyLegitimacyStatus status, Instant verifiedAt) {
        String payload = propertyId + "|" + registryNumber + "|" + claimedOwner + "|" + status.name() + "|" + verifiedAt;
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(payload.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 no disponible en la JVM", e);
        }
    }
}
