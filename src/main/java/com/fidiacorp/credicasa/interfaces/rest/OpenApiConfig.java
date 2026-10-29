package com.fidiacorp.credicasa.interfaces.rest;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfig {
    @Bean
    public OpenAPI customOpenAPI() {
        return new OpenAPI().info(new Info().title("CrediCasa — API del Sprint 2").version("1.0.0")
            .description("Cuentas, segundo factor TOTP, catálogo, ofertas DEMO, avisos y cronogramas persistentes. "
                +"BCP, Interbank y las referencias SBS se representan mediante datos/adaptadores simulados; "
                +"no son aprobaciones bancarias ni consultas oficiales en tiempo real. No se publica una garantía de latencia. "
                +"Los permisos por recurso se obtienen del principal autenticado. Los importes y tasas de catálogo, ofertas "
                +"y simulaciones se devuelven como cadenas decimales sin notación científica; el DTO bancario heredado usa números JSON."))
            .components(new Components().addSecuritySchemes("BearerAuth",new SecurityScheme()
                .type(SecurityScheme.Type.HTTP).scheme("bearer").bearerFormat("JWT")
                .description("Token de POST /api/v1/auth/login. Solo se emite después del TOTP válido cuando está activo. "
                    +"El segundo factor usa códigos de 30 segundos; no se reutiliza el código de confirmación.")));
    }
}
