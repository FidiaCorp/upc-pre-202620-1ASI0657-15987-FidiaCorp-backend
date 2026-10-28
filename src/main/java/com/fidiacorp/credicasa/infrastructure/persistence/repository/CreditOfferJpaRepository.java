package com.fidiacorp.credicasa.infrastructure.persistence.repository;
import com.fidiacorp.credicasa.infrastructure.persistence.entity.CreditOfferJpaEntity;
import org.springframework.data.domain.*;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.*;
public interface CreditOfferJpaRepository extends JpaRepository<CreditOfferJpaEntity,UUID> {
    Optional<CreditOfferJpaEntity> findByIdAndRecipientId(UUID id,UUID recipientId);
    @org.springframework.data.jpa.repository.Lock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
    @org.springframework.data.jpa.repository.Query("select o from CreditOfferJpaEntity o where o.id=:id and o.recipientId=:recipient")
    Optional<CreditOfferJpaEntity> lockForRecipient(@org.springframework.data.repository.query.Param("id") UUID id,
        @org.springframework.data.repository.query.Param("recipient") UUID recipient);
    Page<CreditOfferJpaEntity> findByRecipientId(UUID recipientId,Pageable pageable);
}
