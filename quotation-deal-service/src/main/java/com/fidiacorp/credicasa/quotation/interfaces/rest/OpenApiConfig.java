package com.fidiacorp.credicasa.quotation.interfaces.rest;

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
                .info(new Info().title("CrediCasa · quotation-deal-service (MS-4)").version(API_VERSION)
                        .description("US10–US12, US21 y US22: emisión DEMO de ofertas (destinatario e inmueble validados por API interna, sin FKs), cronograma del destinatario calculado por mortgage-pricing-service con su JWT, versiones inmutables con snapshot SHA-256 y vigencia, exportación PDF (OpenPDF) y XLSX desde el mismo snapshot con registro en export_jobs, y Transactional Outbox hacia RabbitMQ (credicasa.events: credit.offer.issued.v1, quotation.version.issued.v1). Los errores siguen RFC 7807 (application/problem+json) y llevan correlationId. "
                                + "Todas las rutas se consumen a través del API Gateway (:8080) con el mismo prefijo /api/v1."))
                .servers(List.of(new Server().url("/").description("Vía API Gateway o acceso directo al microservicio")))
                .components(new Components().addSecuritySchemes("BearerAuth", new SecurityScheme()
                        .type(SecurityScheme.Type.HTTP).scheme("bearer").bearerFormat("JWT")
                        .description("JWT HS256 emitido por POST /api/v1/auth/login (identity-subscription-service) y validado "
                                + "sin estado por la librería compartida credicasa-shared-kernel.")));
    }
}
