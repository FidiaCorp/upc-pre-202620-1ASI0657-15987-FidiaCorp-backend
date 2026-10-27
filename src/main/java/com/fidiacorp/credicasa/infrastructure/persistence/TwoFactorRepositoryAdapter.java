package com.fidiacorp.credicasa.infrastructure.persistence;
import com.fidiacorp.credicasa.domain.model.TwoFactorState;
import com.fidiacorp.credicasa.domain.ports.out.TwoFactorRepositoryPort;
import com.fidiacorp.credicasa.infrastructure.persistence.entity.TwoFactorJpaEntity;
import com.fidiacorp.credicasa.infrastructure.persistence.repository.*;
import org.springframework.stereotype.Repository;
import java.util.UUID;
@Repository
public class TwoFactorRepositoryAdapter implements TwoFactorRepositoryPort {
    private final AccountJpaRepository accounts;
    private final TwoFactorJpaRepository states;
    public TwoFactorRepositoryAdapter(AccountJpaRepository accounts,TwoFactorJpaRepository states) {
        this.accounts=accounts;this.states=states;
    }
    public TwoFactorState lockForAccount(UUID id) {
        accounts.findForUpdate(id).orElseThrow(() -> new IllegalArgumentException("Cuenta no encontrada."));
        return states.findById(id).map(e -> {
            TwoFactorState s=new TwoFactorState(id);s.enabled=e.enabled;s.encryptedSecret=e.encryptedSecret;
            s.pendingExpiresAt=e.pendingExpiresAt;s.lastAcceptedStep=e.lastAcceptedStep;
            s.failedAttempts=e.failedAttempts;s.lockedUntil=e.lockedUntil;return s;
        }).orElseGet(() -> new TwoFactorState(id));
    }
    public void save(TwoFactorState s) {
        TwoFactorJpaEntity e=states.findById(s.accountId).orElseGet(TwoFactorJpaEntity::new);
        e.accountId=s.accountId;e.account=accounts.getReferenceById(s.accountId);
        e.enabled=s.enabled;e.encryptedSecret=s.encryptedSecret;e.pendingExpiresAt=s.pendingExpiresAt;
        e.lastAcceptedStep=s.lastAcceptedStep;e.failedAttempts=s.failedAttempts;e.lockedUntil=s.lockedUntil;
        states.saveAndFlush(e);
    }
}
