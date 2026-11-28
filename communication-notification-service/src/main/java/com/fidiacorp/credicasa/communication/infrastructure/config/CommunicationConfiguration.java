package com.fidiacorp.credicasa.communication.infrastructure.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Clock;

@Configuration
public class CommunicationConfiguration {
    @Bean
    public Clock clock() {
        return Clock.systemUTC();
    }
}
