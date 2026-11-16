package com.fidiacorp.credicasa.infrastructure.persistence;
import com.fidiacorp.credicasa.domain.model.*;
import com.fidiacorp.credicasa.domain.ports.out.PropertyRepositoryPort;
import com.fidiacorp.credicasa.infrastructure.persistence.entity.PropertyJpaEntity;
import com.fidiacorp.credicasa.infrastructure.persistence.repository.PropertyJpaRepository;
import org.springframework.data.domain.*;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Repository;
import jakarta.persistence.criteria.Predicate;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.*;
@Repository
public class PropertyRepositoryAdapter implements PropertyRepositoryPort {
    private final PropertyJpaRepository repository;
    public PropertyRepositoryAdapter(PropertyJpaRepository repository) {this.repository=repository;}
    public Property save(Property property) {
        PropertyJpaEntity e=new PropertyJpaEntity();e.id=property.id();e.ownerId=property.ownerId();
        e.title=property.title();e.address=property.address();e.description=property.description();
        e.price=property.price();e.currency=property.currency();e.tags=new HashSet<>(property.tags());e.createdAt=property.createdAt();
        return map(repository.saveAndFlush(e));
    }
    public Optional<Property> findActiveById(UUID id) {return repository.findByIdAndDeletedAtIsNull(id).map(this::map);}
    public PropertyPage search(PropertySearch filter) {
        Specification<PropertyJpaEntity> specification=(root,query,cb) -> {
            List<Predicate> predicates=new ArrayList<>();predicates.add(cb.isNull(root.get("deletedAt")));
            if(filter.ownerId()!=null)predicates.add(cb.equal(root.get("ownerId"),filter.ownerId()));
            if(!filter.query().isBlank()) {
                String pattern="%"+filter.query().replace("\\","\\\\").replace("%","\\%").replace("_","\\_")+"%";
                predicates.add(cb.or(cb.like(cb.lower(root.get("title")),pattern,'\\'),
                    cb.like(cb.lower(root.get("address")),pattern,'\\'),cb.like(cb.lower(root.get("description")),pattern,'\\')));
            }
            for(String tag:filter.tags())predicates.add(cb.isMember(tag,root.get("tags")));
            return cb.and(predicates.toArray(Predicate[]::new));
        };
        var page=repository.findAll(specification,PageRequest.of(filter.page(),filter.size(),
            Sort.by(Sort.Order.desc("createdAt"),Sort.Order.desc("id"))));
        return new PropertyPage(page.getContent().stream().map(this::map).toList(),page.getTotalElements());
    }
    public boolean softDelete(UUID id,UUID ownerId,Instant at) {return repository.softDelete(id,ownerId,at)==1;}

    @Override
    public void updateLegitimacy(UUID propertyId, String registryNumber, PropertyLegitimacyStatus status, String office, String notes, Instant verifiedAt) {
        repository.findByIdAndDeletedAtIsNull(propertyId).ifPresent(entity -> {
            entity.registryNumber = registryNumber;
            entity.registryStatus = status != null ? status.name() : PropertyLegitimacyStatus.PENDING_VERIFICATION.name();
            entity.registryOffice = office;
            entity.registryNotes = notes;
            entity.registryVerifiedAt = verifiedAt;
            repository.saveAndFlush(entity);
        });
    }

    @Override
    public Optional<PropertyLegitimacyReport> findLegitimacyByPropertyId(UUID propertyId) {
        return repository.findByIdAndDeletedAtIsNull(propertyId).map(entity -> {
            PropertyLegitimacyStatus status = PropertyLegitimacyStatus.PENDING_VERIFICATION;
            if (entity.registryStatus != null) {
                try {
                    status = PropertyLegitimacyStatus.valueOf(entity.registryStatus);
                } catch (IllegalArgumentException ignored) {}
            }
            LocalDateTime dt = entity.registryVerifiedAt != null
                    ? LocalDateTime.ofInstant(entity.registryVerifiedAt, ZoneId.systemDefault())
                    : LocalDateTime.now();
            return new PropertyLegitimacyReport(
                    entity.id,
                    entity.registryNumber != null ? entity.registryNumber : "",
                    status,
                    entity.registryOffice != null ? entity.registryOffice : "Zona Registral N° IX - Sede Lima",
                    "Titular Registral Verificado",
                    dt,
                    entity.registryNotes != null ? entity.registryNotes : "Estado pendiente de verificación."
            );
        });
    }

    private Property map(PropertyJpaEntity e) {
        return new Property(e.id,e.ownerId,e.title,e.address,e.description,e.price,e.currency,e.tags,e.createdAt);
    }
}
