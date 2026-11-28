package com.fidiacorp.credicasa.catalog.application.dto.response;

import com.fidiacorp.credicasa.catalog.domain.model.PropertyLegitimacyReport;
import com.fidiacorp.credicasa.catalog.domain.model.PropertyLegitimacyStatus;

import java.time.Instant;
import java.util.UUID;

/** Certificado de legitimidad registral (US09). {@code status} y {@code legitimacyStatus} tienen el mismo valor. */
public record PropertyLegitimacyResponse(
        UUID propertyId,
        String registryNumber,
        PropertyLegitimacyStatus status,
        PropertyLegitimacyStatus legitimacyStatus,
        boolean genuine,
        String registryOffice,
        String legalOwnerName,
        Instant verifiedAt,
        Instant legitimacyVerifiedAt,
        String verificationNotes,
        String legitimacyNotes,
        String certificateHash
) {
    public static PropertyLegitimacyResponse from(PropertyLegitimacyReport r) {
        return new PropertyLegitimacyResponse(r.getPropertyId(), r.getRegistryNumber(), r.getStatus(), r.getStatus(), r.isGenuine(),
                r.getRegistryOffice(), r.getLegalOwnerName(), r.getVerifiedAt(), r.getVerifiedAt(), r.getVerificationNotes(),
                r.getVerificationNotes(), r.getCertificateHash());
    }
}
