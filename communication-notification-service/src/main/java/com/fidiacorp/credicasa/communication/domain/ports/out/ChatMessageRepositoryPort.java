package com.fidiacorp.credicasa.communication.domain.ports.out;

import com.fidiacorp.credicasa.communication.domain.model.ChatMessage;

import java.util.List;
import java.util.UUID;

public interface ChatMessageRepositoryPort {
    ChatMessage save(ChatMessage message);
    List<ChatMessage> findByConversationId(UUID conversationId);
    long countByConversationId(UUID conversationId);
}
