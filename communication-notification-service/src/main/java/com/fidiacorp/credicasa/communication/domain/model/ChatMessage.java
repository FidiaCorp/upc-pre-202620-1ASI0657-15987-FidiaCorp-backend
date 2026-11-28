package com.fidiacorp.credicasa.communication.domain.model;

import java.time.LocalDateTime;
import java.util.Objects;
import java.util.UUID;

public class ChatMessage {
    private final UUID id;
    private final UUID conversationId;
    private final UUID senderId;
    private final UUID recipientId;
    private final String senderName;
    private final String content;
    private final LocalDateTime sentAt;

    public ChatMessage(UUID id, UUID conversationId, UUID senderId, UUID recipientId,
                       String senderName, String content, LocalDateTime sentAt) {
        this.id = Objects.requireNonNull(id, "id must not be null");
        this.conversationId = Objects.requireNonNull(conversationId, "conversationId must not be null");
        this.senderId = Objects.requireNonNull(senderId, "senderId must not be null");
        this.recipientId = Objects.requireNonNull(recipientId, "recipientId must not be null");
        this.senderName = Objects.requireNonNull(senderName, "senderName must not be null");
        this.content = Objects.requireNonNull(content, "content must not be null");
        this.sentAt = sentAt != null ? sentAt : LocalDateTime.now();
    }

    public UUID getId() { return id; }
    public UUID getConversationId() { return conversationId; }
    public UUID getSenderId() { return senderId; }
    public UUID getRecipientId() { return recipientId; }
    public String getSenderName() { return senderName; }
    public String getContent() { return content; }
    public LocalDateTime getSentAt() { return sentAt; }
}
