package com.fidiacorp.credicasa;

import com.fidiacorp.credicasa.domain.service.FinancialMetricsCalculator;
import com.fidiacorp.credicasa.domain.service.FrenchAmortizationEngine;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.context.annotation.Bean;

@SpringBootApplication
@EnableCaching
public class CrediCasaApplication {

    public static void main(String[] args) {
        SpringApplication.run(CrediCasaApplication.class, args);
    }

    /**
     * Registro de beans del Dominio en el contenedor de inversión de control (IoC).
     * Mantiene los servicios de dominio puros y sin acoplamiento a anotaciones de Spring.
     */
    @Bean
    public FinancialMetricsCalculator financialMetricsCalculator() {
        return new FinancialMetricsCalculator();
    }

    @Bean
    public FrenchAmortizationEngine frenchAmortizationEngine(FinancialMetricsCalculator metricsCalculator) {
        return new FrenchAmortizationEngine(metricsCalculator);
    }
}
