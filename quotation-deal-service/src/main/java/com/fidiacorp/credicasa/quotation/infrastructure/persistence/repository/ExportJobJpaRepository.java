package com.fidiacorp.credicasa.quotation.infrastructure.persistence.repository;

import com.fidiacorp.credicasa.quotation.infrastructure.persistence.entity.ExportJobJpaEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface ExportJobJpaRepository extends JpaRepository<ExportJobJpaEntity, UUID> {
    List<ExportJobJpaEntity> findByOfferIdOrderByRequestedAtDesc(UUID offerId);
}
