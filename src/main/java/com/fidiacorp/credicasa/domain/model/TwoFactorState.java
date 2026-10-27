package com.fidiacorp.credicasa.domain.model;
import java.time.Instant;
import java.util.UUID;
/** Security state is never returned directly by the REST API. */
public class TwoFactorState {
    public final UUID accountId;
    public boolean enabled;
    public String encryptedSecret;
    public Instant pendingExpiresAt;
    public long lastAcceptedStep = -1;
    public int failedAttempts;
    public Instant lockedUntil;
    public TwoFactorState(UUID accountId) { this.accountId = accountId; }
}
