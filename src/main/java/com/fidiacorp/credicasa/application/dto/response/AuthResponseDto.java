package com.fidiacorp.credicasa.application.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

@Schema(description = "Respuesta de autenticación con JSON Web Token (JWT)")
public record AuthResponseDto(
        @Schema(description = "Token JWT firmado", example = "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9...")
        String token,

        @Schema(description = "Tipo de esquema de autorización", example = "Bearer")
        String type,

        @Schema(description = "Correo del usuario autenticado", example = "asesor@credicasa.pe")
        String email,

        @Schema(description = "Roles RBAC asignados", example = "[\"ROLE_REALTOR\"]")
        List<String> roles,

        @Schema(description = "Tiempo de expiración en milisegundos", example = "86400000")
        long expiresIn
) {
}
