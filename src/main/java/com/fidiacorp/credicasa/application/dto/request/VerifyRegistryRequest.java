package com.fidiacorp.credicasa.application.dto.request;

import jakarta.validation.constraints.NotBlank;

public record VerifyRegistryRequest(
        @NotBlank(message = "registryNumber is required")
        String registryNumber,

        String claimedOwner
) {}
