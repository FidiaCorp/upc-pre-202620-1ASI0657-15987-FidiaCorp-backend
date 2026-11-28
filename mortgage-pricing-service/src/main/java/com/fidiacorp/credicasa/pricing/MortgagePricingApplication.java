package com.fidiacorp.credicasa.pricing;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/** MS-3 mortgage-pricing-service (:8083, pricing_db): US11, US12, US19, US20 + QA-02 Resilience4j. */
@SpringBootApplication
public class MortgagePricingApplication {
    public static void main(String[] args) {
        SpringApplication.run(MortgagePricingApplication.class, args);
    }
}
