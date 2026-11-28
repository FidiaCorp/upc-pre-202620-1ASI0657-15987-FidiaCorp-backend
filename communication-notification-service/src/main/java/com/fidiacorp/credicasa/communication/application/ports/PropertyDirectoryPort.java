package com.fidiacorp.credicasa.communication.application.ports;

import java.util.UUID;

/** property-catalog-service por API interna: ¿el inmueble existe y está activo? */
public interface PropertyDirectoryPort {
    boolean isActive(UUID propertyId);
}
