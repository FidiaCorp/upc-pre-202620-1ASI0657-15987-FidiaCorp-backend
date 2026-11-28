package com.fidiacorp.credicasa.communication.infrastructure.persistence.repository;

import com.fidiacorp.credicasa.communication.infrastructure.persistence.entity.ChatMessageJpaEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface ChatMessageJpaRepository extends JpaRepository<ChatMessageJpaEntity, UUID> {

    List<ChatMessageJpaEntity> findByConversationIdOrderBySentAtAsc(UUID conversationId);

    long countByConversationId(UUID conversationId);
}
