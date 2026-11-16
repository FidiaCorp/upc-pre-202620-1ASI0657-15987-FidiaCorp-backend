package com.fidiacorp.credicasa.application.dto.response;

import java.time.LocalDateTime;
import java.util.UUID;

public record UserBlockResponse(
        UUID blockId,
        UUID blockerId,
        UUID blockedId,
        String reason,
        LocalDateTime createdAt,
        String message
) {}
