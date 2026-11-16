package com.fidiacorp.credicasa.application.dto.request;

import jakarta.validation.constraints.Size;

public record BlockUserRequest(
        @Size(max = 250, message = "reason must not exceed 250 characters")
        String reason
) {}
