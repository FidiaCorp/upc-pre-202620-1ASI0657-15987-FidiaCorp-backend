package com.fidiacorp.credicasa.catalog.domain.model;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/** Resultado inmutable de una verificación registral SUNARP (US09), sellado con un hash SHA-256. */
public class PropertyLegitimacyReport {
    public static final String DEFAULT_OFFICE = "Zona Registral N° IX - Sede Lima";

    private final UUID propertyId;
    private final String registryNumber;
    private final PropertyLegitimacyStatus status;
    private final String registryOffice;
    private final String legalOwnerName;
    private final Instant verifiedAt;
    private final String verificationNotes;
    private final String certificateHash;

    public PropertyLegitimacyReport(UUID propertyId, String registryNumber, PropertyLegitimacyStatus status,
                                    String registryOffice, String legalOwnerName, Instant verifiedAt,
                                    String verificationNotes, String certificateHash) {
        this.propertyId = Objects.requireNonNull(propertyId, "propertyId must not be null");
        this.registryNumber = Objects.requireNonNull(registryNumber, "registryNumber must not be null");
        this.status = Objects.requireNonNull(status, "status must not be null");
        this.registryOffice = registryOffice != null ? registryOffice : DEFAULT_OFFICE;
        this.legalOwnerName = legalOwnerName;
        this.verifiedAt = verifiedAt;
        this.verificationNotes = verificationNotes;
        this.certificateHash = certificateHash;
    }

    /** Reporte por defecto de un inmueble nunca verificado. */
    public static PropertyLegitimacyReport pending(UUID propertyId) {
        return new PropertyLegitimacyReport(propertyId, "", PropertyLegitimacyStatus.PENDING_VERIFICATION, DEFAULT_OFFICE,
                null, null, "Inmueble pendiente de validación registral.", null);
    }

    public boolean isGenuine() { return status == PropertyLegitimacyStatus.VERIFIED_GENUINE; }
    public UUID getPropertyId() { return propertyId; }
    public String getRegistryNumber() { return registryNumber; }
    public PropertyLegitimacyStatus getStatus() { return status; }
    public String getRegistryOffice() { return registryOffice; }
    public String getLegalOwnerName() { return legalOwnerName; }
    public Instant getVerifiedAt() { return verifiedAt; }
    public String getVerificationNotes() { return verificationNotes; }
    public String getCertificateHash() { return certificateHash; }
}
