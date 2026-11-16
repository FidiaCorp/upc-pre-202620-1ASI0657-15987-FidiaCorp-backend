package com.fidiacorp.credicasa.infrastructure.persistence.repository;

import com.fidiacorp.credicasa.infrastructure.persistence.entity.ChatMessageJpaEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface ChatMessageJpaRepository extends JpaRepository<ChatMessageJpaEntity, UUID> {

    List<ChatMessageJpaEntity> findByConversationIdOrderBySentAtAsc(UUID conversationId);

    @Query("SELECT COUNT(m) > 0 FROM ChatMessageJpaEntity m WHERE m.conversationId = :conversationId AND (m.senderId = :accountId OR m.recipientId = :accountId)")
    boolean hasAccessToConversation(@Param("accountId") UUID accountId, @Param("conversationId") UUID conversationId);
}
