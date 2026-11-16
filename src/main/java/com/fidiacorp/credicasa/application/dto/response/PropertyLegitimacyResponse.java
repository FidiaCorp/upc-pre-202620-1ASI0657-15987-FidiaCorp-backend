package com.fidiacorp.credicasa.application.dto.response;

import com.fidiacorp.credicasa.domain.model.PropertyLegitimacyStatus;

import java.time.LocalDateTime;
import java.util.UUID;

public record PropertyLegitimacyResponse(
        UUID propertyId,
        String registryNumber,
        PropertyLegitimacyStatus status,
        boolean genuine,
        String registryOffice,
        String legalOwnerName,
        LocalDateTime verifiedAt,
        String verificationNotes
) {}
