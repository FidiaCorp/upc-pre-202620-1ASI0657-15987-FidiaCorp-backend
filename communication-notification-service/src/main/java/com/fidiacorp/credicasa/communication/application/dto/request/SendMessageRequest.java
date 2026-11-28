package com.fidiacorp.credicasa.communication.application.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.UUID;

/** recipientId es opcional: el destinatario se deriva de la conversación; si se envía debe coincidir con la contraparte. */
public record SendMessageRequest(
        @NotNull(message = "conversationId is required")
        UUID conversationId,

        UUID recipientId,

        @NotBlank(message = "content must not be blank")
        @Size(max = 2000, message = "content must not exceed 2000 characters")
        String content
) {}
