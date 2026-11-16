package com.fidiacorp.credicasa.domain.model;

import java.time.LocalDateTime;
import java.util.Objects;
import java.util.UUID;

public class PropertyLegitimacyReport {
    private final UUID propertyId;
    private final String registryNumber;
    private final PropertyLegitimacyStatus status;
    private final String registryOffice;
    private final String legalOwnerName;
    private final LocalDateTime verifiedAt;
    private final String verificationNotes;

    public PropertyLegitimacyReport(UUID propertyId, String registryNumber, PropertyLegitimacyStatus status,
                                    String registryOffice, String legalOwnerName, LocalDateTime verifiedAt,
                                    String verificationNotes) {
        this.propertyId = Objects.requireNonNull(propertyId, "propertyId must not be null");
        this.registryNumber = Objects.requireNonNull(registryNumber, "registryNumber must not be null");
        this.status = Objects.requireNonNull(status, "status must not be null");
        this.registryOffice = registryOffice != null ? registryOffice : "Zona Registral N° IX - Sede Lima";
        this.legalOwnerName = legalOwnerName != null ? legalOwnerName : "Propietario Verificado Sunarp";
        this.verifiedAt = verifiedAt != null ? verifiedAt : LocalDateTime.now();
        this.verificationNotes = verificationNotes != null ? verificationNotes : "Verificación exitosa en el Registro de Predios de la Sunarp.";
    }

    public UUID getPropertyId() { return propertyId; }
    public String getRegistryNumber() { return registryNumber; }
    public PropertyLegitimacyStatus getStatus() { return status; }
    public String getRegistryOffice() { return registryOffice; }
    public String getLegalOwnerName() { return legalOwnerName; }
    public LocalDateTime getVerifiedAt() { return verifiedAt; }
    public String getVerificationNotes() { return verificationNotes; }
}
