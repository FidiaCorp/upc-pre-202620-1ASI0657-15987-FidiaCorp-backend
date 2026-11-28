package com.fidiacorp.credicasa.gateway;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Clock;

@Configuration
public class GatewayConfiguration {
    @Bean
    public Clock clock() {
        return Clock.systemUTC();
    }
}
