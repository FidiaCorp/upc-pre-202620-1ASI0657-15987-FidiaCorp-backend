package com.fidiacorp.credicasa.quotation.application.dto.external;

import java.util.List;
import java.util.UUID;

public record AccountSummary(UUID id, String displayName, String email, List<String> roles) {
    public boolean isClient() {
        return roles != null && roles.contains("ROLE_CLIENT");
    }
}
