package com.fidiacorp.credicasa.quotation.infrastructure.persistence.repository;

import com.fidiacorp.credicasa.quotation.infrastructure.persistence.entity.QuotationVersionJpaEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface QuotationVersionJpaRepository extends JpaRepository<QuotationVersionJpaEntity, UUID> {
    List<QuotationVersionJpaEntity> findByOfferIdOrderByVersionNumberDesc(UUID offerId);
    Optional<QuotationVersionJpaEntity> findByOfferIdAndVersionNumber(UUID offerId, int versionNumber);
    Optional<QuotationVersionJpaEntity> findFirstByOfferIdOrderByVersionNumberDesc(UUID offerId);
    @Query("select coalesce(max(v.versionNumber), 0) from QuotationVersionJpaEntity v where v.offerId = :offer")
    int maxVersion(@Param("offer") UUID offerId);
}
