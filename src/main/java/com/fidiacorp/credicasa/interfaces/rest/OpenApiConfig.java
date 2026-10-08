package com.fidiacorp.credicasa.interfaces.rest;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.License;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfig {

    private static final String SECURITY_SCHEME_NAME = "BearerAuth";

    @Bean
    public OpenAPI customOpenAPI() {
        return new OpenAPI()
                .info(new Info()
                        .title("CrediCasa Mortgage Calculation Core API")
                        .description("Microservicio Core del Motor Financiero y Simulador Hipotecario de FidiaCorp.\n\n" +
                                "Implementa los 3 Drivers Arquitectónicos:\n" +
                                "- **ASR-SEC**: Spring Security 6 con JWT Stateless y RBAC (ROLE_CLIENT, ROLE_REALTOR)\n" +
                                "- **ASR-PERF**: Motor Francés Ordinario Vencido (30/360), saldo final 0.00 exacto, gracia, cuota balón, TIR/TCEA y caché SBS sub-100ms\n" +
                                "- **ASR-MOD**: Arquitectura Hexagonal DDD (Ports & Adapters) con integración desacoplada de BCP, Interbank y SBS")
                        .version("1.0.0")
                        .contact(new Contact()
                                .name("FidiaCorp Architecture Team")
                                .email("architecture@fidiacorp.pe")
                                .url("https://credicasa.pe"))
                        .license(new License().name("Apache 2.0").url("https://www.apache.org/licenses/LICENSE-2.0")))
                .addSecurityItem(new SecurityRequirement().addList(SECURITY_SCHEME_NAME))
                .components(new Components()
                        .addSecuritySchemes(SECURITY_SCHEME_NAME,
                                new SecurityScheme()
                                        .name(SECURITY_SCHEME_NAME)
                                        .type(SecurityScheme.Type.HTTP)
                                        .scheme("bearer")
                                        .bearerFormat("JWT")
                                        .description("Ingrese el token JWT obtenido del endpoint /api/v1/auth/login")));
    }
}
