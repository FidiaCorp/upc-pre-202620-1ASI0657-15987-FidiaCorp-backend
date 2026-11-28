package com.fidiacorp.credicasa.pricing.domain.model;

/** QA-02: origen de las tasas de mercado — proveedor externo en vivo o caché SBS de respaldo (circuito abierto). */
public enum DataSource {
    LIVE,
    FALLBACK_CACHED
}
