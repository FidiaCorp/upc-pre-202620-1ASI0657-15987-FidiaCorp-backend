package com.fidiacorp.credicasa.catalog.domain.ports.out;

import com.fidiacorp.credicasa.catalog.domain.model.PropertyLegitimacyReport;

import java.util.UUID;

/** Puerto de salida hacia el Registro de Predios SUNARP; el adaptador sella el reporte con SHA-256. */
public interface SunarpRegistryVerificationPort {
    PropertyLegitimacyReport verifyRegistryNumber(UUID propertyId, String registryNumber, String claimedOwner);
}
