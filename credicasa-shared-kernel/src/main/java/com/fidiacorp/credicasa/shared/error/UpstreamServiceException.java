package com.fidiacorp.credicasa.shared.error;

/** Un microservicio dependiente no respondió o respondió con error inesperado (HTTP 503). */
public class UpstreamServiceException extends RuntimeException {
    public UpstreamServiceException(String message, Throwable cause) {
        super(message, cause);
    }
}
