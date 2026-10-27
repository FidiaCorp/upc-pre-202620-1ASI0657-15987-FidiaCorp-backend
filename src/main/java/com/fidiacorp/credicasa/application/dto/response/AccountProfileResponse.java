package com.fidiacorp.credicasa.application.dto.response;
import com.fidiacorp.credicasa.domain.model.Account;
import java.util.Set;
import java.util.UUID;
public record AccountProfileResponse(UUID id, String firstName, String lastName, String email, Set<String> roles) {
    public static AccountProfileResponse from(Account account) {
        return new AccountProfileResponse(account.id(), account.firstName(), account.lastName(), account.email(), account.roles());
    }
}
