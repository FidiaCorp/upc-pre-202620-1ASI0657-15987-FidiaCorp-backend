package com.fidiacorp.credicasa.shared.web;

import org.slf4j.MDC;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpRequest;
import org.springframework.http.client.ClientHttpRequestExecution;
import org.springframework.http.client.ClientHttpRequestInterceptor;
import org.springframework.http.client.ClientHttpResponse;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.io.IOException;

/**
 * Interceptor para RestClient entre microservicios: propaga X-Correlation-Id (y X-Internal-Token en rutas internas) y, si la petición entrante trae un
 * Authorization Bearer y la saliente no define uno propio, lo reenvía (identidad del usuario extremo a extremo).
 */
public class OutboundHeadersInterceptor implements ClientHttpRequestInterceptor {
    private final String internalToken;

    public OutboundHeadersInterceptor() {
        this(null);
    }

    /** @param internalToken secreto compartido que se adjunta solo a rutas {@code /api/v1/internal/**}. */
    public OutboundHeadersInterceptor(String internalToken) {
        this.internalToken = internalToken == null || internalToken.isBlank() ? null : internalToken;
    }

    @Override
    public ClientHttpResponse intercept(HttpRequest request, byte[] body, ClientHttpRequestExecution execution) throws IOException {
        HttpHeaders headers = request.getHeaders();
        String correlationId = MDC.get(CorrelationId.MDC_KEY);
        if (correlationId != null && !headers.containsKey(CorrelationId.HEADER)) {
            headers.set(CorrelationId.HEADER, correlationId);
        }
        if (!headers.containsKey(HttpHeaders.AUTHORIZATION)
                && RequestContextHolder.getRequestAttributes() instanceof ServletRequestAttributes attributes) {
            String authorization = attributes.getRequest().getHeader(HttpHeaders.AUTHORIZATION);
            if (authorization != null && authorization.startsWith("Bearer ")) {
                headers.set(HttpHeaders.AUTHORIZATION, authorization);
            }
        }
        if (internalToken != null && request.getURI().getPath().startsWith(InternalApiGuardFilter.INTERNAL_PREFIX)) {
            headers.set(InternalApiGuardFilter.HEADER, internalToken);
        }
        return execution.execute(request, body);
    }
}
