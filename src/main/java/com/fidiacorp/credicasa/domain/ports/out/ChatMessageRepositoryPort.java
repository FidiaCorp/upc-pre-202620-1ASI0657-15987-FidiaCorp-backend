package com.fidiacorp.credicasa.domain.ports.out;

import com.fidiacorp.credicasa.domain.model.ChatMessage;

import java.util.List;
import java.util.UUID;

public interface ChatMessageRepositoryPort {
    ChatMessage save(ChatMessage message);
    List<ChatMessage> findByConversationId(UUID conversationId);
    boolean hasAccessToConversation(UUID accountId, UUID conversationId);
}
