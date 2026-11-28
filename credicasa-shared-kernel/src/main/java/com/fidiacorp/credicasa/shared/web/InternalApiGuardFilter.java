package com.fidiacorp.credicasa.shared.web;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

/**
 * Defensa en profundidad para {@code /api/v1/internal/**}: el API Gateway nunca enruta esas rutas y, si se configura
 * {@code INTERNAL_API_TOKEN}, cada llamada servicio-a-servicio debe presentar {@code X-Internal-Token} (comparación en
 * tiempo constante). Una petición sin token válido recibe 404 para no revelar la existencia del endpoint.
 */
public class InternalApiGuardFilter extends OncePerRequestFilter {
    public static final String HEADER = "X-Internal-Token";
    public static final String INTERNAL_PREFIX = "/api/v1/internal/";
    private final byte[] expected;

    public InternalApiGuardFilter(String internalToken) {
        this.expected = internalToken == null || internalToken.isBlank() ? null : internalToken.getBytes(StandardCharsets.UTF_8);
    }

    public boolean enforced() {
        return expected != null;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return expected == null || !request.getRequestURI().startsWith(INTERNAL_PREFIX);
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String presented = request.getHeader(HEADER);
        if (presented == null || !MessageDigest.isEqual(expected, presented.getBytes(StandardCharsets.UTF_8))) {
            // Respuesta escrita directamente (no sendError): un dispatch a /error lo volvería a evaluar Spring Security
            // y un llamante sin JWT recibiría 401, revelando que la ruta existe. Siempre 404 ProblemDetail.
            response.setStatus(HttpServletResponse.SC_NOT_FOUND);
            response.setContentType("application/problem+json");
            response.setCharacterEncoding(StandardCharsets.UTF_8.name());
            response.getWriter().write("{\"type\":\"about:blank\",\"title\":\"Not Found\",\"status\":404,\"error\":\"Not Found\",\"message\":\"Recurso no disponible.\"}");
            return;
        }
        chain.doFilter(request, response);
    }
}
