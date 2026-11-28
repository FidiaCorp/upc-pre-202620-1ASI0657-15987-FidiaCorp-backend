package com.fidiacorp.credicasa.pricing.application.usecase;

import com.fidiacorp.credicasa.pricing.application.dto.response.ScheduleExportFile;
import com.fidiacorp.credicasa.pricing.domain.model.Quotation;
import com.fidiacorp.credicasa.pricing.domain.model.QuotationNotFoundException;
import com.fidiacorp.credicasa.pricing.domain.ports.out.QuotationRepositoryPort;
import com.fidiacorp.credicasa.pricing.domain.ports.out.ScheduleExportPort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

/** Caso de uso US12: exporta a XLSX una simulación propia mediante {@link ScheduleExportPort}. */
@Service
@Transactional(readOnly = true)
public class ExportScheduleService {
    private final QuotationRepositoryPort quotations;
    private final ScheduleExportPort exporter;

    public ExportScheduleService(QuotationRepositoryPort quotations, ScheduleExportPort exporter) {
        this.quotations = quotations;
        this.exporter = exporter;
    }

    public ScheduleExportFile exportSimulation(UUID ownerId, UUID quotationId) {
        Quotation quotation = quotations.findByIdAndOwnerId(quotationId, ownerId).orElseThrow(QuotationNotFoundException::new);
        return new ScheduleExportFile("cronograma_simulacion_" + quotationId + "." + exporter.fileExtension(), exporter.mediaType(),
                exporter.export(quotation, "Cronograma Simulacion CrediCasa - " + quotationId));
    }
}
