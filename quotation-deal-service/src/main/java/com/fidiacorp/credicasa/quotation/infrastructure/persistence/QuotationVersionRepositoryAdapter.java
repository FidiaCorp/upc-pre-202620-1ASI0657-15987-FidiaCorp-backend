package com.fidiacorp.credicasa.quotation.infrastructure.persistence;

import com.fidiacorp.credicasa.quotation.domain.model.QuotationVersion;
import com.fidiacorp.credicasa.quotation.domain.ports.out.QuotationVersionRepositoryPort;
import com.fidiacorp.credicasa.quotation.infrastructure.persistence.entity.QuotationVersionJpaEntity;
import com.fidiacorp.credicasa.quotation.infrastructure.persistence.repository.QuotationVersionJpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public class QuotationVersionRepositoryAdapter implements QuotationVersionRepositoryPort {
    private final QuotationVersionJpaRepository repository;

    public QuotationVersionRepositoryAdapter(QuotationVersionJpaRepository repository) {
        this.repository = repository;
    }

    @Override
    public QuotationVersion append(QuotationVersion v) {
        if (repository.existsById(v.id())) throw new IllegalStateException("Las versiones de cotización son inmutables.");
        QuotationVersionJpaEntity e = new QuotationVersionJpaEntity();
        e.id = v.id(); e.offerId = v.offerId(); e.versionNumber = v.versionNumber(); e.loanAmount = v.loanAmount();
        e.annualInterestRate = v.annualInterestRate(); e.termMonths = v.termMonths(); e.snapshotJson = v.snapshotJson();
        e.snapshotHash = v.snapshotHash(); e.issuedBy = v.issuedBy(); e.simulationId = v.simulationId(); e.issuedAt = v.issuedAt();
        e.expiresAt = v.expiresAt();
        return map(repository.saveAndFlush(e));
    }

    @Override
    public List<QuotationVersion> findByOffer(UUID offerId) {
        return repository.findByOfferIdOrderByVersionNumberDesc(offerId).stream().map(this::map).toList();
    }

    @Override
    public Optional<QuotationVersion> findByOfferAndNumber(UUID offerId, int versionNumber) {
        return repository.findByOfferIdAndVersionNumber(offerId, versionNumber).map(this::map);
    }

    @Override
    public Optional<QuotationVersion> findLatest(UUID offerId) {
        return repository.findFirstByOfferIdOrderByVersionNumberDesc(offerId).map(this::map);
    }

    @Override
    public Optional<QuotationVersion> findById(UUID id) {
        return repository.findById(id).map(this::map);
    }

    @Override
    public int nextVersionNumber(UUID offerId) {
        return repository.maxVersion(offerId) + 1;
    }

    private QuotationVersion map(QuotationVersionJpaEntity e) {
        return new QuotationVersion(e.id, e.offerId, e.versionNumber, e.loanAmount, e.annualInterestRate, e.termMonths, e.snapshotJson,
                e.snapshotHash, e.issuedBy, e.simulationId, e.issuedAt, e.expiresAt);
    }
}
