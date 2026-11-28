package com.fidiacorp.credicasa.shared.web;

import java.util.UUID;
import java.util.regex.Pattern;

/** Reglas de X-Correlation-Id compartidas por gateway y microservicios. */
public final class CorrelationId {
    public static final String HEADER = "X-Correlation-Id";
    public static final String MDC_KEY = "correlationId";
    private static final Pattern VALID = Pattern.compile("[A-Za-z0-9-]{1,64}");

    private CorrelationId() {
    }

    /** Devuelve el valor entrante si es seguro (sin inyección en logs/cabeceras) o genera uno nuevo. */
    public static String sanitizeOrGenerate(String incoming) {
        if (incoming != null && VALID.matcher(incoming).matches()) {
            return incoming;
        }
        return UUID.randomUUID().toString();
    }
}
