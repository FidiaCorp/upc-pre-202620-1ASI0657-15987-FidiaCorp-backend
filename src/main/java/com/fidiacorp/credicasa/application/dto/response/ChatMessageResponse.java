package com.fidiacorp.credicasa.application.dto.response;

import java.time.LocalDateTime;
import java.util.UUID;

public record ChatMessageResponse(
        UUID messageId,
        UUID conversationId,
        UUID senderId,
        UUID recipientId,
        String senderName,
        String content,
        LocalDateTime sentAt
) {}
