package com.fidiacorp.credicasa.domain.model;
public enum OfferBank {
    BCP("Banco de Crédito del Perú"), INTERBANK("Interbank");
    private final String displayName;
    OfferBank(String displayName) {this.displayName=displayName;}
    public String displayName() {return displayName;}
    public String demoTitle() {return "Oferta demostrativa de "+displayName;}
}
