package com.fidiacorp.credicasa.catalog.application.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record VerifyRegistryRequest(
        @NotBlank(message = "registryNumber is required")
        @Size(max = 50, message = "registryNumber must not exceed 50 characters")
        String registryNumber,

        @Size(max = 200, message = "claimedOwner must not exceed 200 characters")
        String claimedOwner
) {}
