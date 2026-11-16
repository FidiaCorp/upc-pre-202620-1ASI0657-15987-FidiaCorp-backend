package com.fidiacorp.credicasa.infrastructure.persistence;

import com.fidiacorp.credicasa.domain.model.ChatMessage;
import com.fidiacorp.credicasa.domain.ports.out.ChatMessageRepositoryPort;
import com.fidiacorp.credicasa.infrastructure.persistence.entity.ChatMessageJpaEntity;
import com.fidiacorp.credicasa.infrastructure.persistence.repository.ChatMessageJpaRepository;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.UUID;

@Component
public class ChatMessageRepositoryAdapter implements ChatMessageRepositoryPort {

    private final ChatMessageJpaRepository repository;

    public ChatMessageRepositoryAdapter(ChatMessageJpaRepository repository) {
        this.repository = repository;
    }

    @Override
    public ChatMessage save(ChatMessage message) {
        ChatMessageJpaEntity e = new ChatMessageJpaEntity();
        e.id = message.getId();
        e.conversationId = message.getConversationId();
        e.senderId = message.getSenderId();
        e.recipientId = message.getRecipientId();
        e.senderName = message.getSenderName();
        e.content = message.getContent();
        e.sentAt = message.getSentAt();
        ChatMessageJpaEntity saved = repository.save(e);
        return new ChatMessage(saved.id, saved.conversationId, saved.senderId, saved.recipientId, saved.senderName, saved.content, saved.sentAt);
    }

    @Override
    public List<ChatMessage> findByConversationId(UUID conversationId) {
        return repository.findByConversationIdOrderBySentAtAsc(conversationId).stream()
                .map(e -> new ChatMessage(e.id, e.conversationId, e.senderId, e.recipientId, e.senderName, e.content, e.sentAt))
                .toList();
    }

    @Override
    public boolean hasAccessToConversation(UUID accountId, UUID conversationId) {
        return repository.hasAccessToConversation(accountId, conversationId);
    }
}
