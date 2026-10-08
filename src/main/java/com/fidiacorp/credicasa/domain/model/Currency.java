package com.fidiacorp.credicasa.domain.model;

public enum Currency {
    PEN("S/.", "Soles"),
    USD("$", "Dólares Americanos");

    private final String symbol;
    private final String description;

    Currency(String symbol, String description) {
        this.symbol = symbol;
        this.description = description;
    }

    public String getSymbol() {
        return symbol;
    }

    public String getDescription() {
        return description;
    }
}
