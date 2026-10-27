package com.fidiacorp.credicasa.infrastructure.persistence.repository;
import com.fidiacorp.credicasa.infrastructure.persistence.entity.OfferNotificationJpaEntity;
import org.springframework.data.domain.*;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;
import java.time.Instant;
import java.util.*;
public interface OfferNotificationJpaRepository extends JpaRepository<OfferNotificationJpaEntity,UUID> {
    Page<OfferNotificationJpaEntity> findByRecipientId(UUID recipientId,Pageable page);
    Page<OfferNotificationJpaEntity> findByRecipientIdAndReadAtIsNull(UUID recipientId,Pageable page);
    Optional<OfferNotificationJpaEntity> findByIdAndRecipientId(UUID id,UUID recipientId);
    long countByRecipientIdAndReadAtIsNull(UUID recipientId);
    @Modifying(clearAutomatically=true,flushAutomatically=true)
    @Query("update OfferNotificationJpaEntity n set n.readAt=:at where n.id=:id and n.recipientId=:recipient and n.readAt is null")
    int markRead(@Param("id") UUID id,@Param("recipient") UUID recipient,@Param("at") Instant at);
}
