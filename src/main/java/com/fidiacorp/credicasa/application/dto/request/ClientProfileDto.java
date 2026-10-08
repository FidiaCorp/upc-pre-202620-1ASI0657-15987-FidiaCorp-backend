package com.fidiacorp.credicasa.application.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.PositiveOrZero;

import java.math.BigDecimal;

@Schema(description = "Datos del perfil del solicitante del crédito")
public record ClientProfileDto(
        @Schema(description = "Número de documento (DNI/CE)", example = "72849102")
        @NotBlank(message = "El número de documento es obligatorio")
        String documentNumber,

        @Schema(description = "Nombres y apellidos completos", example = "Carlos Alberto Mendoza Ramos")
        @NotBlank(message = "El nombre completo es obligatorio")
        String fullName,

        @Schema(description = "Correo electrónico del cliente", example = "carlos.mendoza@gmail.com")
        @Email(message = "Formato de correo electrónico inválido")
        String email,

        @Schema(description = "Ingreso neto mensual en la moneda del préstamo", example = "8500.00")
        @PositiveOrZero(message = "El ingreso mensual debe ser mayor o igual a cero")
        BigDecimal monthlyIncome,

        @Schema(description = "Score crediticio en centrales de riesgo (1 - 1000)", example = "780")
        Integer creditScore
) {
}
