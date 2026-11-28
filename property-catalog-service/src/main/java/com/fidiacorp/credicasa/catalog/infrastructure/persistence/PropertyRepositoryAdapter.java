package com.fidiacorp.credicasa.catalog.infrastructure.persistence;
import com.fidiacorp.credicasa.catalog.domain.model.*;
import com.fidiacorp.credicasa.catalog.domain.ports.out.PropertyRepositoryPort;
import com.fidiacorp.credicasa.catalog.infrastructure.persistence.entity.PropertyJpaEntity;
import com.fidiacorp.credicasa.catalog.infrastructure.persistence.repository.PropertyJpaRepository;
import org.springframework.data.domain.*;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Repository;
import jakarta.persistence.criteria.Predicate;
import java.time.Instant;
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
    public void updateLegitimacy(PropertyLegitimacyReport report) {
        PropertyJpaEntity entity=repository.findByIdAndDeletedAtIsNull(report.getPropertyId()).orElseThrow(PropertyNotFoundException::new);
        entity.registryNumber=report.getRegistryNumber();
        entity.registryStatus=report.getStatus().name();
        entity.registryOffice=report.getRegistryOffice();
        entity.registryNotes=report.getVerificationNotes();
        entity.registryOwnerName=report.getLegalOwnerName();
        entity.registryVerifiedAt=report.getVerifiedAt();
        entity.registryCertificateHash=report.getCertificateHash();
        repository.saveAndFlush(entity);
    }

    @Override
    public Optional<PropertyLegitimacyReport> findLegitimacyByPropertyId(UUID propertyId) {
        return repository.findByIdAndDeletedAtIsNull(propertyId).map(this::legitimacy);
    }

    private PropertyLegitimacyReport legitimacy(PropertyJpaEntity e) {
        if(e.registryVerifiedAt==null || e.registryStatus==null)return PropertyLegitimacyReport.pending(e.id);
        PropertyLegitimacyStatus status;
        try {status=PropertyLegitimacyStatus.valueOf(e.registryStatus);}
        catch(IllegalArgumentException ignored) {status=PropertyLegitimacyStatus.PENDING_VERIFICATION;}
        return new PropertyLegitimacyReport(e.id,e.registryNumber==null?"":e.registryNumber,status,e.registryOffice,
            e.registryOwnerName,e.registryVerifiedAt,e.registryNotes,e.registryCertificateHash);
    }

    private Property map(PropertyJpaEntity e) {
        return new Property(e.id,e.ownerId,e.title,e.address,e.description,e.price,e.currency,e.tags,e.createdAt,legitimacy(e));
    }
}
