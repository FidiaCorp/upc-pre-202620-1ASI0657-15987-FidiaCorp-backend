package com.fidiacorp.credicasa.quotation.application.ports;

import com.fidiacorp.credicasa.quotation.application.dto.snapshot.QuotationSnapshot;
import com.fidiacorp.credicasa.quotation.domain.model.ExportFormat;

/** US22: genera PDF/XLSX exclusivamente desde el snapshot inmutable de una versión. */
public interface ScheduleDocumentRenderer {
    ExportFormat format();
    byte[] render(QuotationSnapshot snapshot, String snapshotHash);
}
