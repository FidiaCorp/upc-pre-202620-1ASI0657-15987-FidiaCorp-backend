package com.fidiacorp.credicasa.communication.domain.model;

import com.fidiacorp.credicasa.shared.error.ResourceNotFoundException;

public class ConversationNotFoundException extends ResourceNotFoundException {
    public ConversationNotFoundException() {
        super("La conversación no existe.");
    }
}
