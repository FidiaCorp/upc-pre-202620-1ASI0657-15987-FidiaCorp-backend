package com.fidiacorp.credicasa.identity;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

/** MS-1 identity-subscription-service (:8081, identity_db): US01–US06. */
@SpringBootApplication
@EnableScheduling
public class IdentitySubscriptionApplication {
    public static void main(String[] args) {
        SpringApplication.run(IdentitySubscriptionApplication.class, args);
    }
}
