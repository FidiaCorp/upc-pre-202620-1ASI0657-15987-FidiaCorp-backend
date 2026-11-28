package com.fidiacorp.credicasa.shared.error;

/** Recurso inexistente o no disponible para el principal (HTTP 404). */
public class ResourceNotFoundException extends RuntimeException {
    public ResourceNotFoundException(String message) {
        super(message);
    }
}
