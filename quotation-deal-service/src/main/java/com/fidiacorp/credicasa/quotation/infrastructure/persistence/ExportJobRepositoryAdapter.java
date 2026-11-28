package com.fidiacorp.credicasa.quotation.infrastructure.persistence;

import com.fidiacorp.credicasa.quotation.domain.model.ExportJob;
import com.fidiacorp.credicasa.quotation.domain.ports.out.ExportJobRepositoryPort;
import com.fidiacorp.credicasa.quotation.infrastructure.persistence.entity.ExportJobJpaEntity;
import com.fidiacorp.credicasa.quotation.infrastructure.persistence.repository.ExportJobJpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public class ExportJobRepositoryAdapter implements ExportJobRepositoryPort {
    private final ExportJobJpaRepository repository;

    public ExportJobRepositoryAdapter(ExportJobJpaRepository repository) {
        this.repository = repository;
    }

    @Override
    public ExportJob save(ExportJob j) {
        ExportJobJpaEntity e = new ExportJobJpaEntity();
        e.id = j.id(); e.offerId = j.offerId(); e.versionId = j.versionId(); e.versionNumber = j.versionNumber(); e.format = j.format();
        e.status = j.status(); e.requestedBy = j.requestedBy(); e.fileName = j.fileName(); e.contentSha256 = j.contentSha256();
        e.sizeBytes = j.sizeBytes(); e.snapshotHash = j.snapshotHash(); e.requestedAt = j.requestedAt(); e.completedAt = j.completedAt();
        e.errorMessage = j.errorMessage();
        return map(repository.saveAndFlush(e));
    }

    @Override
    public List<ExportJob> findByOffer(UUID offerId) {
        return repository.findByOfferIdOrderByRequestedAtDesc(offerId).stream().map(this::map).toList();
    }

    private ExportJob map(ExportJobJpaEntity e) {
        return new ExportJob(e.id, e.offerId, e.versionId, e.versionNumber, e.format, e.status, e.requestedBy, e.fileName, e.contentSha256,
                e.sizeBytes, e.snapshotHash, e.requestedAt, e.completedAt, e.errorMessage);
    }
}
