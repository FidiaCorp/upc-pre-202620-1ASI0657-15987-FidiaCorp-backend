package com.fidiacorp.credicasa.domain.ports.out;

import com.fidiacorp.credicasa.domain.model.Quotation;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface QuotationRepositoryPort {
    Quotation save(Quotation quotation);
    Optional<Quotation> findById(UUID id);
    List<Quotation> findAll();
}
