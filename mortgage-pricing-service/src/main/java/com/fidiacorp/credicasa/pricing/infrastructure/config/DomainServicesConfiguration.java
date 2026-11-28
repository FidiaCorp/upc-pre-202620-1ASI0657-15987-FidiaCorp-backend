package com.fidiacorp.credicasa.pricing.infrastructure.config;

import com.fidiacorp.credicasa.pricing.domain.service.AmortizationStrategy;
import com.fidiacorp.credicasa.pricing.domain.service.FinancialMetricsCalculator;
import com.fidiacorp.credicasa.pricing.domain.service.FrenchAmortizationEngine;
import com.fidiacorp.credicasa.pricing.domain.service.ScenarioComparisonEngine;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Clock;

import java.util.concurrent.Executors;

/** Registro explícito (IoC) de servicios de dominio puros y del ejecutor del @TimeLimiter (QA-02). */
@Configuration
@EnableCaching
public class DomainServicesConfiguration {
    @Bean
    public Clock clock() {
        return Clock.systemUTC();
    }

    @Bean
    public FinancialMetricsCalculator financialMetricsCalculator() {
        return new FinancialMetricsCalculator();
    }

    @Bean
    public AmortizationStrategy amortizationStrategy(FinancialMetricsCalculator metrics) {
        return new FrenchAmortizationEngine(metrics);
    }

    @Bean
    public ScenarioComparisonEngine scenarioComparisonEngine(AmortizationStrategy strategy, FinancialMetricsCalculator metrics) {
        return new ScenarioComparisonEngine(strategy, metrics);
    }

    @Bean(destroyMethod = "shutdown")
    public java.util.concurrent.ExecutorService bankingProviderExecutor() {
        return Executors.newFixedThreadPool(8, runnable -> {
            Thread thread = new Thread(runnable, "banking-provider");
            thread.setDaemon(true);
            return thread;
        });
    }
}
