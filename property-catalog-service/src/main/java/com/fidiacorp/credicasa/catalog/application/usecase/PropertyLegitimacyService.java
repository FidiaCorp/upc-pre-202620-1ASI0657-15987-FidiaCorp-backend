package com.fidiacorp.credicasa.catalog.application.usecase;

import com.fidiacorp.credicasa.catalog.application.dto.request.VerifyRegistryRequest;
import com.fidiacorp.credicasa.catalog.application.dto.response.PropertyLegitimacyResponse;
import com.fidiacorp.credicasa.catalog.domain.model.Property;
import com.fidiacorp.credicasa.catalog.domain.model.PropertyLegitimacyReport;
import com.fidiacorp.credicasa.catalog.domain.model.PropertyNotFoundException;
import com.fidiacorp.credicasa.catalog.domain.ports.out.PropertyRepositoryPort;
import com.fidiacorp.credicasa.catalog.domain.ports.out.SunarpRegistryVerificationPort;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

/** US09: solo el propietario del inmueble puede solicitar la verificación registral (anti BOLA/IDOR). */
@Service
@Transactional
public class PropertyLegitimacyService {
    private static final Logger log = LoggerFactory.getLogger(PropertyLegitimacyService.class);

    private final PropertyRepositoryPort propertyRepository;
    private final SunarpRegistryVerificationPort sunarpPort;

    public PropertyLegitimacyService(PropertyRepositoryPort propertyRepository, SunarpRegistryVerificationPort sunarpPort) {
        this.propertyRepository = propertyRepository;
        this.sunarpPort = sunarpPort;
    }

    public PropertyLegitimacyResponse verifyPropertyLegitimacy(UUID requesterId, UUID propertyId, VerifyRegistryRequest request) {
        Property property = propertyRepository.findActiveById(propertyId)
                .orElseThrow(() -> new PropertyNotFoundException("Inmueble con ID " + propertyId + " no encontrado."));
        if (!property.isOwnedBy(requesterId)) {
            log.warn("[SECURITY-AUDIT] BOLA bloqueado: cuenta {} intentó verificar el registro del inmueble ajeno {}", requesterId, propertyId);
            throw new AccessDeniedException("Solo el propietario puede solicitar la verificación registral del inmueble.");
        }
        PropertyLegitimacyReport report = sunarpPort.verifyRegistryNumber(property.id(), request.registryNumber(), request.claimedOwner());
        propertyRepository.updateLegitimacy(report);
        return PropertyLegitimacyResponse.from(report);
    }

    @Transactional(readOnly = true)
    public PropertyLegitimacyResponse getPropertyLegitimacy(UUID propertyId) {
        propertyRepository.findActiveById(propertyId)
                .orElseThrow(() -> new PropertyNotFoundException("Inmueble con ID " + propertyId + " no encontrado."));
        return PropertyLegitimacyResponse.from(propertyRepository.findLegitimacyByPropertyId(propertyId)
                .orElseGet(() -> PropertyLegitimacyReport.pending(propertyId)));
    }
}
