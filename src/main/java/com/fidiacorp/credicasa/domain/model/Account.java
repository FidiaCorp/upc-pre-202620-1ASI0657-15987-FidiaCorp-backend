package com.fidiacorp.credicasa.domain.model;

import java.util.Set;
import java.util.UUID;

public record Account(UUID id, String firstName, String lastName, String email,
                      String passwordHash, Set<String> roles) {
    public Account { roles = Set.copyOf(roles); }
}
