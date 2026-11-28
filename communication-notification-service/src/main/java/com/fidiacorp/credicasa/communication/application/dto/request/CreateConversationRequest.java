package com.fidiacorp.credicasa.communication.application.dto.request;

import jakarta.validation.constraints.NotNull;

import java.util.UUID;

/** US16: inicia (o recupera) una conversación con otra cuenta, opcionalmente desde un inmueble del catálogo. */
public record CreateConversationRequest(
        @NotNull(message = "recipientId is required") UUID recipientId,
        UUID propertyId
) {}
