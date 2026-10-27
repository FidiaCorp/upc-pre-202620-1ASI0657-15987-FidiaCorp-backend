package com.fidiacorp.credicasa.application.dto.request;
import jakarta.validation.constraints.*;
public record TwoFactorDisableRequest(@NotBlank @Size(max=256) String password,
    @NotBlank @Pattern(regexp="[0-9]{6}") String otp) {}
