package com.fidiacorp.credicasa.pricing.application.dto.response;

/** Archivo binario de cronograma listo para descargar (US12). */
public record ScheduleExportFile(String filename, String mediaType, byte[] content) {}
