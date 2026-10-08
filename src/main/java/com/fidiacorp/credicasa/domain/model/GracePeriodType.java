package com.fidiacorp.credicasa.domain.model;

public enum GracePeriodType {
    NONE("Sin período de gracia"),
    PARTIAL("Gracia Parcial (solo pago de intereses y seguros, no amortiza capital)"),
    TOTAL("Gracia Total (intereses se capitalizan al saldo deudor, amortización 0)");

    private final String description;

    GracePeriodType(String description) {
        this.description = description;
    }

    public String getDescription() {
        return description;
    }
}
