package com.fidiacorp.credicasa.quotation.domain.model;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * US21: versión inmutable de la cotización de una oferta. {@code snapshotJson} congela términos y cronograma; su
 * SHA-256 ({@code snapshotHash}) permite verificar que nadie la alteró. Nunca se actualiza: una nueva negociación crea
 * la versión {@code n + 1}.
 */
public record QuotationVersion(UUID id, UUID offerId, int versionNumber, BigDecimal loanAmount, BigDecimal annualInterestRate,
                               int termMonths, String snapshotJson, String snapshotHash, UUID issuedBy, UUID simulationId,
                               Instant issuedAt, Instant expiresAt) {
    public QuotationVersion {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(offerId, "offerId");
        Objects.requireNonNull(snapshotJson, "snapshotJson");
        if (versionNumber < 1) throw new IllegalArgumentException("versionNumber debe ser >= 1");
        if (snapshotHash == null || !snapshotHash.matches("[0-9a-f]{64}")) throw new IllegalArgumentException("snapshotHash SHA-256 inválido");
        if (expiresAt == null || issuedAt == null || !expiresAt.isAfter(issuedAt)) throw new IllegalArgumentException("La vigencia debe ser posterior a la emisión");
    }

    public boolean expired(Instant now) {
        return !now.isBefore(expiresAt);
    }

    public boolean intact() {
        return SnapshotHasher.sha256(snapshotJson).equals(snapshotHash);
    }
}
