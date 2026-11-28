package com.fidiacorp.credicasa.pricing.domain.ports.out;

import com.fidiacorp.credicasa.pricing.domain.model.Quotation;

/**
 * Puerto de salida (US12): exporta el cronograma de una cotización a un formato binario.
 * Recibe el agregado de dominio puro {@link Quotation}; nunca DTOs de aplicación.
 */
public interface ScheduleExportPort {
    /** Media type del archivo generado por el adaptador. */
    String mediaType();

    /** Extensión de archivo sin punto (por ejemplo {@code xlsx}). */
    String fileExtension();

    byte[] export(Quotation quotation, String title);
}
