package com.fidiacorp.credicasa.shared.web;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.MDC;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/** Propaga X-Correlation-Id: lo valida o genera, lo publica en el MDC y lo devuelve en la respuesta. */
public class CorrelationIdFilter extends OncePerRequestFilter {
    public static final String REQUEST_ATTRIBUTE = CorrelationIdFilter.class.getName() + ".value";

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String correlationId = CorrelationId.sanitizeOrGenerate(request.getHeader(CorrelationId.HEADER));
        request.setAttribute(REQUEST_ATTRIBUTE, correlationId);
        response.setHeader(CorrelationId.HEADER, correlationId);
        MDC.put(CorrelationId.MDC_KEY, correlationId);
        try {
            chain.doFilter(request, response);
        } finally {
            MDC.remove(CorrelationId.MDC_KEY);
        }
    }

    /** Valor vigente en el hilo actual (para propagarlo a llamadas salientes y eventos). */
    public static String current() {
        String value = MDC.get(CorrelationId.MDC_KEY);
        return value == null ? CorrelationId.sanitizeOrGenerate(null) : value;
    }
}
