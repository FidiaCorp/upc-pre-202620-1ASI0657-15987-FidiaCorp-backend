package com.fidiacorp.credicasa.quotation.infrastructure.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

import java.time.Clock;

@Configuration
@EnableScheduling
public class QuotationConfiguration {
    @Bean
    public Clock clock() {
        return Clock.systemUTC();
    }
}
