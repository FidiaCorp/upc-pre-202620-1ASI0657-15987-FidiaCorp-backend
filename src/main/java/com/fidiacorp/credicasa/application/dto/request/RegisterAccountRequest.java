package com.fidiacorp.credicasa.application.dto.request;
import jakarta.validation.constraints.*;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
public record RegisterAccountRequest(
    @NotBlank @Size(max=100) String firstName,
    @NotBlank @Size(max=100) String lastName,
    @NotBlank @Email @Size(max=254) String email,
    @NotBlank @Size(min=12, max=72) String password
) {
    public RegisterAccountRequest {
        firstName = firstName == null ? null : firstName.trim();
        lastName = lastName == null ? null : lastName.trim();
        email = email == null ? null : email.trim().toLowerCase(Locale.ROOT);
    }
    @AssertTrue(message="La contraseña no debe superar 72 bytes UTF-8.")
    public boolean isPasswordWithinByteLimit() {
        return password == null || password.getBytes(StandardCharsets.UTF_8).length <= 72;
    }
}
