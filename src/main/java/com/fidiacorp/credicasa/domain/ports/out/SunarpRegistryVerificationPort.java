package com.fidiacorp.credicasa.domain.ports.out;

import com.fidiacorp.credicasa.domain.model.PropertyLegitimacyReport;

import java.util.UUID;

public interface SunarpRegistryVerificationPort {
    PropertyLegitimacyReport verifyRegistryNumber(UUID propertyId, String registryNumber, String claimedOwner);
}
