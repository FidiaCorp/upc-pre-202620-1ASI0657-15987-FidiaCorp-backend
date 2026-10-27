package com.fidiacorp.credicasa.application.dto.request;
import jakarta.validation.constraints.*;
public record TwoFactorSetupRequest(@NotBlank @Size(max=256) String password) {}
