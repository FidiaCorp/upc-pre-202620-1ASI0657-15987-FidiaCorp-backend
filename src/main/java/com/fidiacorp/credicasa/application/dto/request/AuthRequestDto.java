package com.fidiacorp.credicasa.application.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

@Schema(description = "Credenciales de autenticación para generación de JWT")
public record AuthRequestDto(
        @Schema(description = "Correo electrónico institucional o de usuario", example = "asesor@credicasa.pe")
        @NotBlank(message = "El correo electrónico es obligatorio")
        @Email(message = "Formato de correo electrónico inválido")
        String email,

        @Schema(description = "Contraseña en texto plano", example = "Asesor123!")
        @NotBlank(message = "La contraseña es obligatoria")
        String password
) {
}
