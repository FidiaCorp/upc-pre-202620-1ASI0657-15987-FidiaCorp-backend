package com.fidiacorp.credicasa.identity.application.dto.response;

import com.fidiacorp.credicasa.identity.domain.model.Account;

import java.util.List;
import java.util.UUID;

/** Vista mínima de una cuenta para otros bounded contexts (sin hash ni datos sensibles). */
public record AccountSummaryResponse(UUID id, String displayName, String email, List<String> roles) {
    public static AccountSummaryResponse from(Account account) {
        return new AccountSummaryResponse(account.id(), (account.firstName() + " " + account.lastName()).strip(), account.email(),
                account.roles().stream().sorted().toList());
    }
}
