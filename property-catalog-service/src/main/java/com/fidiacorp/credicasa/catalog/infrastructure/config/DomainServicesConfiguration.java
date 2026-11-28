package com.fidiacorp.credicasa.catalog.infrastructure.config;

import com.fidiacorp.credicasa.catalog.domain.service.AiPropertyRecommendationEngine;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Clock;

/** Registro explícito (IoC) de los servicios de dominio puros: el dominio no conoce Spring (ASR-MOD / ArchUnit). */
@Configuration
public class DomainServicesConfiguration {
    @Bean
    public Clock clock() {
        return Clock.systemUTC();
    }

    @Bean
    public AiPropertyRecommendationEngine aiPropertyRecommendationEngine() {
        return new AiPropertyRecommendationEngine();
    }
}
