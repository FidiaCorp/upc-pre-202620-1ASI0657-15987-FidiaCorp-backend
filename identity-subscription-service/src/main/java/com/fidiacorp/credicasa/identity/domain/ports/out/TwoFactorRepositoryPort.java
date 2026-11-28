package com.fidiacorp.credicasa.identity.domain.ports.out;
import com.fidiacorp.credicasa.identity.domain.model.TwoFactorState;
import java.util.UUID;
public interface TwoFactorRepositoryPort {
    /** Must be called in a transaction; serializes even the first setup for an account. */
    TwoFactorState lockForAccount(UUID accountId);
    void save(TwoFactorState state);
}
