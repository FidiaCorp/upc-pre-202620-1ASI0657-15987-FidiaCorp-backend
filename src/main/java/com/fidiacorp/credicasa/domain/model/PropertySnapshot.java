package com.fidiacorp.credicasa.domain.model;

import java.math.BigDecimal;
import java.util.Objects;
import java.util.UUID;

public class PropertySnapshot {

    private final UUID propertyId;
    private final BigDecimal propertyValue;
    private final Currency currency;
    private final String address;

    public PropertySnapshot(UUID propertyId, BigDecimal propertyValue, Currency currency, String address) {
        this.propertyId = propertyId != null ? propertyId : UUID.randomUUID();
        this.propertyValue = propertyValue != null ? propertyValue : BigDecimal.ZERO;
        this.currency = currency != null ? currency : Currency.PEN;
        this.address = address;
    }

    public UUID getPropertyId() {
        return propertyId;
    }

    public BigDecimal getPropertyValue() {
        return propertyValue;
    }

    public Currency getCurrency() {
        return currency;
    }

    public String getAddress() {
        return address;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        PropertySnapshot that = (PropertySnapshot) o;
        return Objects.equals(propertyId, that.propertyId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(propertyId);
    }
}
