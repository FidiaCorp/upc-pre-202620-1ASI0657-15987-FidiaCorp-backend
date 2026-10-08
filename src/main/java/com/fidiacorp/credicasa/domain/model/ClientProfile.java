package com.fidiacorp.credicasa.domain.model;

import java.math.BigDecimal;
import java.util.Objects;
import java.util.UUID;

public class ClientProfile {

    private final UUID id;
    private final String documentNumber;
    private final String fullName;
    private final String email;
    private final BigDecimal monthlyIncome;
    private final Integer creditScore;

    public ClientProfile(UUID id, String documentNumber, String fullName, String email, BigDecimal monthlyIncome, Integer creditScore) {
        this.id = id != null ? id : UUID.randomUUID();
        this.documentNumber = documentNumber;
        this.fullName = fullName;
        this.email = email;
        this.monthlyIncome = monthlyIncome;
        this.creditScore = creditScore;
    }

    public UUID getId() {
        return id;
    }

    public String getDocumentNumber() {
        return documentNumber;
    }

    public String getFullName() {
        return fullName;
    }

    public String getEmail() {
        return email;
    }

    public BigDecimal getMonthlyIncome() {
        return monthlyIncome;
    }

    public Integer getCreditScore() {
        return creditScore;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        ClientProfile that = (ClientProfile) o;
        return Objects.equals(documentNumber, that.documentNumber) && Objects.equals(email, that.email);
    }

    @Override
    public int hashCode() {
        return Objects.hash(documentNumber, email);
    }
}
