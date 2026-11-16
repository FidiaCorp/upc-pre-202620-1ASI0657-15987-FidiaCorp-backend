package com.fidiacorp.credicasa.infrastructure.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "chat_messages")
public class ChatMessageJpaEntity {

    @Id
    public UUID id;

    @Column(name = "conversation_id", nullable = false)
    public UUID conversationId;

    @Column(name = "sender_id", nullable = false)
    public UUID senderId;

    @Column(name = "recipient_id", nullable = false)
    public UUID recipientId;

    @Column(name = "sender_name", length = 200, nullable = false)
    public String senderName;

    @Column(name = "content", columnDefinition = "TEXT", nullable = false)
    public String content;

    @Column(name = "sent_at", nullable = false)
    public LocalDateTime sentAt = LocalDateTime.now();

    public ChatMessageJpaEntity() {}
}
