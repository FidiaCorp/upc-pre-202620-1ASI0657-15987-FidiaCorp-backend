package com.fidiacorp.credicasa.infrastructure.security;
import com.fidiacorp.credicasa.application.usecase.TokenRevocationService;
import org.springframework.scheduling.annotation.*;
import org.springframework.stereotype.Component;
@Component
@EnableScheduling
public class TokenRevocationCleanup {
    private final TokenRevocationService service;
    public TokenRevocationCleanup(TokenRevocationService service) {this.service=service;}
    @Scheduled(fixedDelayString="${app.security.jwt.revocation-cleanup-ms:3600000}",
        initialDelayString="${app.security.jwt.revocation-cleanup-ms:3600000}")
    public void purgeExpired() {service.purgeExpired();}
}
