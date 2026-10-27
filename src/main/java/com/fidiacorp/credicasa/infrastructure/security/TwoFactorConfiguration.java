package com.fidiacorp.credicasa.infrastructure.security;
import com.fidiacorp.credicasa.domain.service.TotpAlgorithm;
import org.springframework.context.annotation.*;
import java.time.Clock;
@Configuration
public class TwoFactorConfiguration {
    @Bean public Clock securityClock() { return Clock.systemUTC(); }
    @Bean public TotpAlgorithm totpAlgorithm() { return new TotpAlgorithm(); }
}
