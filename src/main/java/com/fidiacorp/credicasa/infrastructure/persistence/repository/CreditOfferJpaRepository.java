package com.fidiacorp.credicasa.infrastructure.persistence.repository;
import com.fidiacorp.credicasa.infrastructure.persistence.entity.CreditOfferJpaEntity;
import org.springframework.data.domain.*;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.*;
public interface CreditOfferJpaRepository extends JpaRepository<CreditOfferJpaEntity,UUID> {
    Optional<CreditOfferJpaEntity> findByIdAndRecipientId(UUID id,UUID recipientId);
    Page<CreditOfferJpaEntity> findByRecipientId(UUID recipientId,Pageable pageable);
}
