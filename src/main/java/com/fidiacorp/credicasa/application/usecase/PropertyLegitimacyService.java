package com.fidiacorp.credicasa.application.usecase;

import com.fidiacorp.credicasa.application.dto.request.VerifyRegistryRequest;
import com.fidiacorp.credicasa.application.dto.response.PropertyLegitimacyResponse;
import com.fidiacorp.credicasa.domain.model.Property;
import com.fidiacorp.credicasa.domain.model.PropertyLegitimacyReport;
import com.fidiacorp.credicasa.domain.model.PropertyLegitimacyStatus;
import com.fidiacorp.credicasa.domain.model.PropertyNotFoundException;
import com.fidiacorp.credicasa.domain.ports.out.PropertyRepositoryPort;
import com.fidiacorp.credicasa.domain.ports.out.SunarpRegistryVerificationPort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.UUID;

@Service
@Transactional
public class PropertyLegitimacyService {

    private final PropertyRepositoryPort propertyRepository;
    private final SunarpRegistryVerificationPort sunarpPort;

    public PropertyLegitimacyService(PropertyRepositoryPort propertyRepository,
                                    SunarpRegistryVerificationPort sunarpPort) {
        this.propertyRepository = propertyRepository;
        this.sunarpPort = sunarpPort;
    }

    public PropertyLegitimacyResponse verifyPropertyLegitimacy(UUID propertyId, VerifyRegistryRequest request) {
        Property property = propertyRepository.findActiveById(propertyId)
                .orElseThrow(() -> new PropertyNotFoundException("Inmueble con ID " + propertyId + " no encontrado."));

        PropertyLegitimacyReport report = sunarpPort.verifyRegistryNumber(
                property.id(),
                request.registryNumber(),
                request.claimedOwner()
        );

        propertyRepository.updateLegitimacy(
                property.id(),
                report.getRegistryNumber(),
                report.getStatus(),
                report.getRegistryOffice(),
                report.getVerificationNotes(),
                Instant.now()
        );

        return new PropertyLegitimacyResponse(
                report.getPropertyId(),
                report.getRegistryNumber(),
                report.getStatus(),
                report.getStatus() == PropertyLegitimacyStatus.VERIFIED_GENUINE,
                report.getRegistryOffice(),
                report.getLegalOwnerName(),
                report.getVerifiedAt(),
                report.getVerificationNotes()
        );
    }

    @Transactional(readOnly = true)
    public PropertyLegitimacyResponse getPropertyLegitimacy(UUID propertyId) {
        propertyRepository.findActiveById(propertyId)
                .orElseThrow(() -> new PropertyNotFoundException("Inmueble con ID " + propertyId + " no encontrado."));

        PropertyLegitimacyReport report = propertyRepository.findLegitimacyByPropertyId(propertyId)
                .orElseGet(() -> new PropertyLegitimacyReport(
                        propertyId,
                        "",
                        PropertyLegitimacyStatus.PENDING_VERIFICATION,
                        "Zona Registral N° IX - Sede Lima",
                        "Pendiente",
                        null,
                        "Inmueble pendiente de validación registral."
                ));

        return new PropertyLegitimacyResponse(
                report.getPropertyId(),
                report.getRegistryNumber(),
                report.getStatus(),
                report.getStatus() == PropertyLegitimacyStatus.VERIFIED_GENUINE,
                report.getRegistryOffice(),
                report.getLegalOwnerName(),
                report.getVerifiedAt(),
                report.getVerificationNotes()
        );
    }
}
