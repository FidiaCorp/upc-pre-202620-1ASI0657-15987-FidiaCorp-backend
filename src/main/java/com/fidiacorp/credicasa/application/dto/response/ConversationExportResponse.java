package com.fidiacorp.credicasa.application.dto.response;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

public record ConversationExportResponse(
        UUID conversationId,
        int totalMessages,
        LocalDateTime exportedAt,
        List<ChatMessageResponse> messages,
        String formattedTranscript
) {}
