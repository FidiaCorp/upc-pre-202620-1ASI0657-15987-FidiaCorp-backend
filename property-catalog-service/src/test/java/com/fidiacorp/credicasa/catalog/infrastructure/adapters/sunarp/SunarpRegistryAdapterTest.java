package com.fidiacorp.credicasa.catalog.infrastructure.adapters.sunarp;

import com.fidiacorp.credicasa.catalog.domain.model.PropertyLegitimacyReport;
import com.fidiacorp.credicasa.catalog.domain.model.PropertyLegitimacyStatus;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.HexFormat;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/** US09: el certificado es un SHA-256 real y reproducible sobre propertyId|registryNumber|claimedOwner|status|verifiedAt. */
class SunarpRegistryAdapterTest {
    private static final Instant NOW = Instant.parse("2026-11-24T15:30:45.123456789Z");
    private final SunarpRegistryAdapter adapter = new SunarpRegistryAdapter(Clock.fixed(NOW, ZoneOffset.UTC));
    private final UUID propertyId = UUID.fromString("7d1c0e4c-2b62-4c76-9d52-1a4f3c8e9b10");

    private static String sha256(String text) throws Exception {
        return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(text.getBytes(StandardCharsets.UTF_8)));
    }

    @Test
    @DisplayName("Partida válida → VERIFIED_GENUINE con hash SHA-256 verificable y fecha truncada a milisegundos")
    void validRegistryProducesVerifiableHash() throws Exception {
        PropertyLegitimacyReport report = adapter.verifyRegistryNumber(propertyId, " 14298172 ", " Ana Pérez ");
        assertThat(report.getStatus()).isEqualTo(PropertyLegitimacyStatus.VERIFIED_GENUINE);
        assertThat(report.isGenuine()).isTrue();
        assertThat(report.getRegistryNumber()).isEqualTo("14298172");
        assertThat(report.getLegalOwnerName()).isEqualTo("Ana Pérez");
        assertThat(report.getVerifiedAt()).isEqualTo(Instant.parse("2026-11-24T15:30:45.123Z"));
        String expected = sha256(propertyId + "|14298172|Ana Pérez|VERIFIED_GENUINE|2026-11-24T15:30:45.123Z");
        assertThat(report.getCertificateHash()).isEqualTo(expected).hasSize(64);
        assertThat(SunarpRegistryAdapter.certificateHash(propertyId, "14298172", "Ana Pérez", PropertyLegitimacyStatus.VERIFIED_GENUINE,
                report.getVerifiedAt())).isEqualTo(expected);
        assertThat(adapter.verifyRegistryNumber(propertyId, "PE-14298172", "Ana Pérez").getStatus()).isEqualTo(PropertyLegitimacyStatus.VERIFIED_GENUINE);
    }

    @Test
    @DisplayName("Cualquier cambio en los datos sellados cambia el hash")
    void hashChangesWithAnyField() {
        String base = adapter.verifyRegistryNumber(propertyId, "14298172", "Ana").getCertificateHash();
        assertThat(adapter.verifyRegistryNumber(propertyId, "14298173", "Ana").getCertificateHash()).isNotEqualTo(base);
        assertThat(adapter.verifyRegistryNumber(propertyId, "14298172", "Ana B").getCertificateHash()).isNotEqualTo(base);
        assertThat(adapter.verifyRegistryNumber(UUID.randomUUID(), "14298172", "Ana").getCertificateHash()).isNotEqualTo(base);
        assertThat(new SunarpRegistryAdapter(Clock.fixed(NOW.plusMillis(1), ZoneOffset.UTC))
                .verifyRegistryNumber(propertyId, "14298172", "Ana").getCertificateHash()).isNotEqualTo(base);
    }

    @Test
    @DisplayName("Formato inválido, vacío o con carga no saneada → FLAGGED_ANOMALY (también sellado)")
    void anomaliesAreFlagged() {
        for (String number : new String[]{"XYZ-99", "1234567", "", null, "12349999", "PE-00000000"}) {
            PropertyLegitimacyReport report = adapter.verifyRegistryNumber(propertyId, number, null);
            assertThat(report.getStatus()).as(String.valueOf(number)).isEqualTo(PropertyLegitimacyStatus.FLAGGED_ANOMALY);
            assertThat(report.isGenuine()).isFalse();
            assertThat(report.getLegalOwnerName()).isEqualTo("Titular no declarado");
            assertThat(report.getCertificateHash()).hasSize(64);
            assertThat(report.getVerificationNotes()).isNotBlank();
        }
    }

    @Test
    @DisplayName("Reporte pendiente por defecto: sin hash ni fecha")
    void pendingReport() {
        PropertyLegitimacyReport pending = PropertyLegitimacyReport.pending(propertyId);
        assertThat(pending.getStatus()).isEqualTo(PropertyLegitimacyStatus.PENDING_VERIFICATION);
        assertThat(pending.getCertificateHash()).isNull();
        assertThat(pending.getVerifiedAt()).isNull();
        assertThat(pending.getRegistryOffice()).isEqualTo(PropertyLegitimacyReport.DEFAULT_OFFICE);
        assertThat(new PropertyLegitimacyReport(propertyId, "1", PropertyLegitimacyStatus.FLAGGED_ANOMALY, null, null, null, null, null)
                .getRegistryOffice()).isEqualTo(PropertyLegitimacyReport.DEFAULT_OFFICE);
    }
}
