package com.fidiacorp.credicasa.quotation.application.usecase;

import com.fidiacorp.credicasa.quotation.application.dto.response.ExportJobResponse;
import com.fidiacorp.credicasa.quotation.application.dto.response.ExportedFile;
import com.fidiacorp.credicasa.quotation.application.ports.ScheduleDocumentRenderer;
import com.fidiacorp.credicasa.quotation.domain.model.ExportFormat;
import com.fidiacorp.credicasa.quotation.domain.model.ExportJob;
import com.fidiacorp.credicasa.quotation.domain.model.ExportStatus;
import com.fidiacorp.credicasa.quotation.domain.model.OfferResourceNotFoundException;
import com.fidiacorp.credicasa.quotation.domain.model.QuotationVersion;
import com.fidiacorp.credicasa.quotation.domain.model.SnapshotHasher;
import com.fidiacorp.credicasa.quotation.domain.ports.out.CreditOfferRepositoryPort;
import com.fidiacorp.credicasa.quotation.domain.ports.out.ExportJobRepositoryPort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * US22 (+US12 para ofertas): PDF y XLSX se generan desde el MISMO snapshot inmutable de la versión (por defecto la
 * última). Antes de renderizar se verifica el SHA-256 del snapshot; cada intento queda registrado en export_jobs.
 */
@Service
public class ScheduleExportService {
    private final QuotationVersionService versions;
    private final ExportJobRepositoryPort jobs;
    private final CreditOfferRepositoryPort offers;
    private final Map<ExportFormat, ScheduleDocumentRenderer> renderers = new EnumMap<>(ExportFormat.class);
    private final Clock clock;

    public ScheduleExportService(QuotationVersionService versions, ExportJobRepositoryPort jobs, CreditOfferRepositoryPort offers,
                                 List<ScheduleDocumentRenderer> renderers, Clock clock) {
        this.versions = versions;
        this.jobs = jobs;
        this.offers = offers;
        renderers.forEach(r -> this.renderers.put(r.format(), r));
        this.clock = clock;
    }

    @Transactional(noRollbackFor = IllegalStateException.class)
    public ExportedFile export(UUID userId, UUID offerId, Integer versionNumber, ExportFormat format) {
        QuotationVersion version = versions.resolve(userId, offerId, versionNumber);
        var requestedAt = clock.instant();
        if (!version.intact()) {
            jobs.save(new ExportJob(UUID.randomUUID(), offerId, version.id(), version.versionNumber(), format, ExportStatus.FAILED, userId, null,
                    null, 0, version.snapshotHash(), requestedAt, clock.instant(), "Snapshot alterado: el SHA-256 no coincide."));
            throw new IllegalStateException("El snapshot de la versión " + version.versionNumber() + " no supera la verificación SHA-256.");
        }
        byte[] content = renderers.get(format).render(versions.snapshot(version), version.snapshotHash());
        String name = "cotizacion_oferta_" + offerId + "_v" + version.versionNumber() + "." + format.extension();
        String sha = SnapshotHasher.sha256(content);
        jobs.save(new ExportJob(UUID.randomUUID(), offerId, version.id(), version.versionNumber(), format, ExportStatus.COMPLETED, userId, name,
                sha, content.length, version.snapshotHash(), requestedAt, clock.instant(), null));
        return new ExportedFile(name, format.mediaType(), content, sha, version.snapshotHash(), version.versionNumber());
    }

    @Transactional(readOnly = true)
    public List<ExportJobResponse> jobs(UUID userId, UUID offerId) {
        offers.findForParticipant(offerId, userId).orElseThrow(OfferResourceNotFoundException::new);
        return jobs.findByOffer(offerId).stream().map(ExportJobResponse::from).toList();
    }
}
