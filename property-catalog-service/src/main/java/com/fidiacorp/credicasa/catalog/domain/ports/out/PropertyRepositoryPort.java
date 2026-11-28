package com.fidiacorp.credicasa.catalog.domain.ports.out;
import com.fidiacorp.credicasa.catalog.domain.model.*;
import java.time.Instant;
import java.util.*;
public interface PropertyRepositoryPort {
    Property save(Property property);
    Optional<Property> findActiveById(UUID id);
    PropertyPage search(PropertySearch search);
    boolean softDelete(UUID id, UUID ownerId, Instant deletedAt);
    void updateLegitimacy(PropertyLegitimacyReport report);
    Optional<PropertyLegitimacyReport> findLegitimacyByPropertyId(UUID propertyId);
}
