package com.fidiacorp.credicasa.communication.infrastructure.persistence.repository;

import com.fidiacorp.credicasa.communication.infrastructure.persistence.entity.ConversationJpaEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.UUID;

public interface ConversationJpaRepository extends JpaRepository<ConversationJpaEntity, UUID> {

    @Query("SELECT c FROM ConversationJpaEntity c WHERE ((c.participantAId = :a AND c.participantBId = :b) "
            + "OR (c.participantAId = :b AND c.participantBId = :a)) AND c.propertyId IS NULL ORDER BY c.createdAt ASC")
    List<ConversationJpaEntity> findBetweenWithoutProperty(@Param("a") UUID a, @Param("b") UUID b);

    @Query("SELECT c FROM ConversationJpaEntity c WHERE ((c.participantAId = :a AND c.participantBId = :b) "
            + "OR (c.participantAId = :b AND c.participantBId = :a)) AND c.propertyId = :propertyId ORDER BY c.createdAt ASC")
    List<ConversationJpaEntity> findBetweenForProperty(@Param("a") UUID a, @Param("b") UUID b, @Param("propertyId") UUID propertyId);

    @Query("SELECT c FROM ConversationJpaEntity c WHERE c.participantAId = :account OR c.participantBId = :account ORDER BY c.createdAt DESC")
    List<ConversationJpaEntity> findByParticipant(@Param("account") UUID account);
}
