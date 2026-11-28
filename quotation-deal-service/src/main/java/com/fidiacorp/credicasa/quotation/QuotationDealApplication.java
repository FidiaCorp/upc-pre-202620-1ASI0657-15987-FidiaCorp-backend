package com.fidiacorp.credicasa.quotation;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/** MS-4 quotation-deal-service (:8084, quotation_db): US10–US12, US21, US22 + Transactional Outbox → RabbitMQ. */
@SpringBootApplication
public class QuotationDealApplication {
    public static void main(String[] args) {
        SpringApplication.run(QuotationDealApplication.class, args);
    }
}
