package com.fidiacorp.credicasa.application.usecase;
import com.fidiacorp.credicasa.domain.ports.out.TokenRevocationRepositoryPort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.*;
import java.util.*;
import java.nio.charset.StandardCharsets;
import java.security.*;
@Service
public class TokenRevocationService {
    private final TokenRevocationRepositoryPort repository;
    private final Clock clock;
    public TokenRevocationService(TokenRevocationRepositoryPort repository,Clock clock) {
        this.repository=repository;this.clock=clock;
    }
    @Transactional
    public void revoke(UUID accountId,String token,Instant expiresAt) {
        if(expiresAt.isAfter(clock.instant())) repository.revoke(accountId,hash(token),expiresAt);
    }
    @Transactional(readOnly=true)
    public boolean isRevoked(String token) {return repository.isRevoked(hash(token),clock.instant());}
    @Transactional
    public int purgeExpired() {return repository.deleteExpired(clock.instant());}
    private String hash(String token) {
        try {return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
            .digest(token.getBytes(StandardCharsets.UTF_8)));}
        catch(NoSuchAlgorithmException ex) {throw new IllegalStateException("Token digest unavailable",ex);}
    }
}
