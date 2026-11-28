package com.fidiacorp.credicasa.catalog.domain.model;

import com.fidiacorp.credicasa.shared.error.ResourceNotFoundException;

public class PropertyNotFoundException extends ResourceNotFoundException {
    public PropertyNotFoundException() {
        super("El inmueble no existe o fue eliminado.");
    }

    public PropertyNotFoundException(String message) {
        super(message);
    }
}
