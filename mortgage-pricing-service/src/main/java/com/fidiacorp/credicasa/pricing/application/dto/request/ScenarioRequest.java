package com.fidiacorp.credicasa.pricing.application.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

@Schema(description = "US20: un escenario a comparar")
public record ScenarioRequest(
        @Schema(example = "Banco A · TEA 9.5%") @NotBlank(message = "La etiqueta del escenario es obligatoria") @Size(max = 60, message = "Etiqueta de máximo 60 caracteres") String label,
        @NotNull(message = "Los parámetros del escenario son obligatorios") @Valid SimulationRequestDto simulation,
        @Valid ExtraordinaryPaymentRequest extraordinaryPayment) {
}
