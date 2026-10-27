package com.fidiacorp.credicasa.application.dto.request;
import jakarta.validation.constraints.*;
import java.util.Locale;
public record UpdateProfileRequest(
    @NotBlank @Size(max=100) String firstName,
    @NotBlank @Size(max=100) String lastName,
    @NotBlank @Email @Size(max=254) String email
) {
    public UpdateProfileRequest {
        firstName = firstName == null ? null : firstName.trim();
        lastName = lastName == null ? null : lastName.trim();
        email = email == null ? null : email.trim().toLowerCase(Locale.ROOT);
    }
}
