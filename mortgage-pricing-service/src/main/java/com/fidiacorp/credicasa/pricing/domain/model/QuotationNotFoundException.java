package com.fidiacorp.credicasa.pricing.domain.model;

import com.fidiacorp.credicasa.shared.error.ResourceNotFoundException;

public class QuotationNotFoundException extends ResourceNotFoundException {
    public QuotationNotFoundException() {
        super("El cronograma solicitado no está disponible.");
    }
}
