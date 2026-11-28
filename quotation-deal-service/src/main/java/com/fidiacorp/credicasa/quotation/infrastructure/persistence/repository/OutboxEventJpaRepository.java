package com.fidiacorp.credicasa.quotation.infrastructure.persistence.repository;

import com.fidiacorp.credicasa.quotation.infrastructure.persistence.entity.OutboxEventJpaEntity;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.UUID;

public interface OutboxEventJpaRepository extends JpaRepository<OutboxEventJpaEntity, UUID> {
    @Query("select e from OutboxEventJpaEntity e where e.publishedAt is null order by e.createdAt asc, e.id asc")
    List<OutboxEventJpaEntity> findPending(Pageable pageable);
    long countByPublishedAtIsNull();
}
