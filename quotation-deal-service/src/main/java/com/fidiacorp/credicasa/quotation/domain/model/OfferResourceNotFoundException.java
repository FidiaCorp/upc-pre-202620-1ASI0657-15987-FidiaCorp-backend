package com.fidiacorp.credicasa.quotation.domain.model;

import com.fidiacorp.credicasa.shared.error.ResourceNotFoundException;

public class OfferResourceNotFoundException extends ResourceNotFoundException {
    public OfferResourceNotFoundException() {
        super("El recurso solicitado no está disponible.");
    }
}
