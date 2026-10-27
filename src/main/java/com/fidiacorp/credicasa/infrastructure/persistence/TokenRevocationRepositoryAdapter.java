package com.fidiacorp.credicasa.infrastructure.persistence;
import com.fidiacorp.credicasa.domain.ports.out.TokenRevocationRepositoryPort;
import com.fidiacorp.credicasa.infrastructure.persistence.entity.RevokedTokenJpaEntity;
import com.fidiacorp.credicasa.infrastructure.persistence.repository.*;
import org.springframework.stereotype.Repository;
import java.time.Instant;
import java.util.UUID;
@Repository
public class TokenRevocationRepositoryAdapter implements TokenRevocationRepositoryPort {
    private final RevokedTokenJpaRepository tokens;
    private final AccountJpaRepository accounts;
    public TokenRevocationRepositoryAdapter(RevokedTokenJpaRepository tokens,AccountJpaRepository accounts) {
        this.tokens=tokens;this.accounts=accounts;
    }
    public void revoke(UUID accountId,String hash,Instant expiresAt) {
        accounts.findForUpdate(accountId).orElseThrow(() -> new IllegalArgumentException("Cuenta no encontrada."));
        if(!tokens.existsById(hash)) tokens.saveAndFlush(new RevokedTokenJpaEntity(hash,expiresAt));
    }
    public boolean isRevoked(String hash,Instant now) {return tokens.existsByTokenHashAndExpiresAtAfter(hash,now);}
    public int deleteExpired(Instant now) {return tokens.deleteExpired(now);}
}
