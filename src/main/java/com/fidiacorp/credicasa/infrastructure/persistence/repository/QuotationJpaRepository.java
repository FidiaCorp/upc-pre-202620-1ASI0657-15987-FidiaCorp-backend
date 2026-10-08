package com.fidiacorp.credicasa.infrastructure.persistence.repository;

import com.fidiacorp.credicasa.infrastructure.persistence.entity.QuotationJpaEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface QuotationJpaRepository extends JpaRepository<QuotationJpaEntity, UUID> {

    @Query("SELECT q FROM QuotationJpaEntity q LEFT JOIN FETCH q.installments WHERE q.id = :id")
    Optional<QuotationJpaEntity> findByIdWithInstallments(@Param("id") UUID id);
}
