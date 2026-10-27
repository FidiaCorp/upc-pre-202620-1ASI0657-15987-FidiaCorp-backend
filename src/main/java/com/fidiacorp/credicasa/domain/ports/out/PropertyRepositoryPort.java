package com.fidiacorp.credicasa.domain.ports.out;
import com.fidiacorp.credicasa.domain.model.*;
import java.time.Instant;
import java.util.*;
public interface PropertyRepositoryPort {
    Property save(Property property);
    Optional<Property> findActiveById(UUID id);
    PropertyPage search(PropertySearch search);
    boolean softDelete(UUID id, UUID ownerId, Instant deletedAt);
}
