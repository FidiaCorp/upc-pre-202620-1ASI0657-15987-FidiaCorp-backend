package com.fidiacorp.credicasa.communication.infrastructure.persistence.repository;

import com.fidiacorp.credicasa.communication.infrastructure.persistence.entity.InboxEventJpaEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface InboxEventJpaRepository extends JpaRepository<InboxEventJpaEntity, UUID> {
}
