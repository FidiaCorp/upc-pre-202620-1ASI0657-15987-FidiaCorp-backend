package com.fidiacorp.credicasa.communication.domain.model;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

public class ConversationTranscript {
    private final UUID conversationId;
    private final List<ChatMessage> messages;
    private final LocalDateTime generatedAt;

    public ConversationTranscript(UUID conversationId, List<ChatMessage> messages, LocalDateTime generatedAt) {
        this.conversationId = Objects.requireNonNull(conversationId, "conversationId must not be null");
        this.messages = Objects.requireNonNull(messages, "messages must not be null");
        this.generatedAt = generatedAt != null ? generatedAt : LocalDateTime.now();
    }

    public UUID getConversationId() { return conversationId; }
    public List<ChatMessage> getMessages() { return messages; }
    public LocalDateTime getGeneratedAt() { return generatedAt; }

    public String toFormattedText() {
        StringBuilder sb = new StringBuilder();
        sb.append("=================================================================\n");
        sb.append("CREDICASA - HISTORIAL OFICIAL DE CONVERSACION DE MENSAJERIA\n");
        sb.append("ID de Conversacion: ").append(conversationId).append("\n");
        sb.append("Fecha de Exportacion: ").append(generatedAt).append("\n");
        sb.append("Total de Mensajes: ").append(messages.size()).append("\n");
        sb.append("=================================================================\n\n");

        for (ChatMessage msg : messages) {
            sb.append("[").append(msg.getSentAt()).append("] ")
                    .append(msg.getSenderName()).append(" (ID: ").append(msg.getSenderId()).append("):\n")
                    .append("  ").append(msg.getContent()).append("\n\n");
        }

        sb.append("--- Fin de la Transcripcion ---\n");
        return sb.toString();
    }
}
