package com.fidiacorp.credicasa.domain.ports.out;
import java.time.Instant;
import java.util.UUID;
public interface TokenRevocationRepositoryPort {
    /** Account lock serializes repeated logout without revoking any other token. */
    void revoke(UUID accountId, String tokenHash, Instant expiresAt);
    boolean isRevoked(String tokenHash, Instant now);
    int deleteExpired(Instant now);
}
