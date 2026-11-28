package com.fidiacorp.credicasa.quotation.domain.model;

import java.time.Instant;
import java.util.UUID;

/** US22: trazabilidad de cada exportación (qué versión, formato, quién, huella SHA-256 y resultado). */
public record ExportJob(UUID id, UUID offerId, UUID versionId, int versionNumber, ExportFormat format, ExportStatus status,
                        UUID requestedBy, String fileName, String contentSha256, long sizeBytes, String snapshotHash,
                        Instant requestedAt, Instant completedAt, String errorMessage) {
}
