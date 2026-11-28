package com.fidiacorp.credicasa.identity.application.dto.request;
import jakarta.validation.constraints.*;
public record TwoFactorCodeRequest(@NotBlank @Pattern(regexp="[0-9]{6}") String otp) {}
