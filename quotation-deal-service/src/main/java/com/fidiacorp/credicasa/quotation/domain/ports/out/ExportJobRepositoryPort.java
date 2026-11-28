package com.fidiacorp.credicasa.quotation.domain.ports.out;

import com.fidiacorp.credicasa.quotation.domain.model.ExportJob;

import java.util.List;
import java.util.UUID;

public interface ExportJobRepositoryPort {
    ExportJob save(ExportJob job);
    List<ExportJob> findByOffer(UUID offerId);
}
