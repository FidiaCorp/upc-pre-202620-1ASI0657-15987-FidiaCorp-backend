package com.fidiacorp.credicasa.communication.application.dto.response;

import java.time.Instant;
import java.time.LocalDateTime;
import java.util.UUID;

public record ConversationResponse(
        UUID conversationId,
        UUID propertyId,
        UUID participantAId,
        UUID participantBId,
        UUID counterpartId,
        String counterpartName,
        Instant createdAt,
        long messageCount,
        LocalDateTime lastMessageAt,
        String lastMessagePreview
) {}
