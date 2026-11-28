package com.fidiacorp.credicasa.quotation.domain.ports.out;

import com.fidiacorp.credicasa.quotation.domain.model.QuotationVersion;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface QuotationVersionRepositoryPort {
    /** Solo inserción: las versiones son inmutables. */
    QuotationVersion append(QuotationVersion version);
    List<QuotationVersion> findByOffer(UUID offerId);
    Optional<QuotationVersion> findByOfferAndNumber(UUID offerId, int versionNumber);
    Optional<QuotationVersion> findLatest(UUID offerId);
    Optional<QuotationVersion> findById(UUID id);
    int nextVersionNumber(UUID offerId);
}
