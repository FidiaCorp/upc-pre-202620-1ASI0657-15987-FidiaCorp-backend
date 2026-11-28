package com.fidiacorp.credicasa.quotation.infrastructure.clients;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fidiacorp.credicasa.shared.error.ResourceNotFoundException;
import com.fidiacorp.credicasa.shared.error.UpstreamServiceException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;

/**
 * Traducción de respuestas de otros microservicios: 4xx conservan su significado (no abren el circuito) y los
 * fallos de red/5xx se convierten en {@link UpstreamServiceException} (503, cuentan para Resilience4j).
 */
final class DownstreamErrors {
    private static final ObjectMapper JSON = new ObjectMapper();

    private DownstreamErrors() {
    }

    static RuntimeException translate(String service, RestClientException error) {
        if (error instanceof RestClientResponseException response) {
            int status = response.getStatusCode().value();
            String detail = detail(response.getResponseBodyAsString());
            if (status == 404) return new ResourceNotFoundException(detail == null ? "El recurso solicitado no está disponible." : detail);
            if (status == 400 || status == 422) return new IllegalArgumentException(detail == null ? "Solicitud inválida." : detail);
            if (status == 401 || status == 403) return new AccessDeniedException("Operación no autorizada en " + service + ".");
            if (status == 409) return new IllegalStateException(detail == null ? "Conflicto en " + service + "." : detail);
        }
        return new UpstreamServiceException(service + " no disponible temporalmente.", error);
    }

    private static String detail(String body) {
        try {
            JsonNode node = JSON.readTree(body);
            return node.hasNonNull("detail") ? node.get("detail").asText() : null;
        } catch (Exception e) {
            return null;
        }
    }
}
