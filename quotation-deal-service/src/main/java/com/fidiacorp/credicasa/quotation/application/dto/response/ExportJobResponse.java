package com.fidiacorp.credicasa.quotation.application.dto.response;

import com.fidiacorp.credicasa.quotation.domain.model.ExportJob;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;
import java.util.UUID;

@Schema(description = "US22: registro de una exportación PDF/XLSX generada desde el snapshot de una versión")
public record ExportJobResponse(UUID id, UUID offerId, UUID versionId, int versionNumber, String format, String status, UUID requestedBy,
                                String fileName, String contentSha256, long sizeBytes, String snapshotHash, Instant requestedAt,
                                Instant completedAt) {
    public static ExportJobResponse from(ExportJob j) {
        return new ExportJobResponse(j.id(), j.offerId(), j.versionId(), j.versionNumber(), j.format().name(), j.status().name(),
                j.requestedBy(), j.fileName(), j.contentSha256(), j.sizeBytes(), j.snapshotHash(), j.requestedAt(), j.completedAt());
    }
}
