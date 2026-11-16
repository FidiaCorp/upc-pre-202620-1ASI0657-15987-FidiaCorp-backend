package com.fidiacorp.credicasa.application.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.UUID;

public record SendMessageRequest(
        @NotNull(message = "conversationId is required")
        UUID conversationId,

        @NotNull(message = "recipientId is required")
        UUID recipientId,

        @NotBlank(message = "content must not be blank")
        @Size(max = 2000, message = "content must not exceed 2000 characters")
        String content
) {}
