package com.fidiacorp.credicasa.pricing.application.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.List;

@Schema(description = "US20: 2 a 3 escenarios comparados lado a lado (no se persisten)")
public record CompareScenariosRequest(
        @NotNull(message = "Los escenarios son obligatorios") @Size(min = 2, max = 3, message = "Compare entre 2 y 3 escenarios") List<@Valid @NotNull ScenarioRequest> scenarios) {
}
