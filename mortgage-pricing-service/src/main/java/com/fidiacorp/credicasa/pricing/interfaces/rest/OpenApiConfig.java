package com.fidiacorp.credicasa.pricing.interfaces.rest;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityScheme;
import io.swagger.v3.oas.models.servers.Server;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;

/** OpenAPI propio del microservicio; el API Gateway lo agrega en /swagger-ui.html. */
@Configuration
public class OpenApiConfig {
    public static final String API_VERSION = "2.0.0";

    @Bean
    public OpenAPI customOpenAPI() {
        return new OpenAPI()
                .info(new Info().title("CrediCasa · mortgage-pricing-service (MS-3)").version(API_VERSION)
                        .description("US11, US12, US19 y US20: motor francés (AmortizationStrategy) en BigDecimal DECIMAL128/HALF_EVEN, TEA o TNA con capitalización configurable, historial y comparador de 2–3 escenarios con prepago, exportación XLSX y tasas de mercado con Resilience4j (CircuitBreaker + TimeLimiter 1000 ms) contra external-bank-mock y respaldo SBS cacheado (X-Data-Source: LIVE | FALLBACK_CACHED). Los errores siguen RFC 7807 (application/problem+json) y llevan correlationId. "
                                + "Todas las rutas se consumen a través del API Gateway (:8080) con el mismo prefijo /api/v1."))
                .servers(List.of(new Server().url("/").description("Vía API Gateway o acceso directo al microservicio")))
                .components(new Components().addSecuritySchemes("BearerAuth", new SecurityScheme()
                        .type(SecurityScheme.Type.HTTP).scheme("bearer").bearerFormat("JWT")
                        .description("JWT HS256 emitido por POST /api/v1/auth/login (identity-subscription-service) y validado "
                                + "sin estado por la librería compartida credicasa-shared-kernel.")));
    }
}
