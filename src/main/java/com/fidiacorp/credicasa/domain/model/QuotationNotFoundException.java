package com.fidiacorp.credicasa.domain.model;
public class QuotationNotFoundException extends RuntimeException {
    public QuotationNotFoundException() {super("El cronograma solicitado no está disponible.");}
}
