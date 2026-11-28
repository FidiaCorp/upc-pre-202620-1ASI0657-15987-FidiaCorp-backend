package com.fidiacorp.credicasa.pricing.domain.ports.out;

import com.fidiacorp.credicasa.pricing.domain.model.Quotation;
import com.fidiacorp.credicasa.pricing.domain.model.QuotationSummaryPage;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface QuotationRepositoryPort {
    Quotation save(Quotation quotation);
    Optional<Quotation> findById(UUID id);
    Optional<Quotation> findByIdAndOwnerId(UUID id,UUID ownerId);
    List<Quotation> findAll();
    /** US20: historial paginado de simulaciones propias, más recientes primero. */
    QuotationSummaryPage findSummariesByOwner(UUID ownerId, int page, int size);
}
