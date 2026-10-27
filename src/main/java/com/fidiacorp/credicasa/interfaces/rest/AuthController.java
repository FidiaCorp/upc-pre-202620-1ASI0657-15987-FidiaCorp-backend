package com.fidiacorp.credicasa.interfaces.rest;

import com.fidiacorp.credicasa.application.dto.request.AuthRequestDto;
import com.fidiacorp.credicasa.application.dto.response.AuthResponseDto;
import com.fidiacorp.credicasa.infrastructure.security.JwtTokenProvider;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/v1/auth")
@Tag(name = "Authentication & Access Control (ASR-SEC)", description = "Endpoints de autenticación y emisión de tokens JWT con roles RBAC")
public class AuthController {

    private final AuthenticationManager authenticationManager;
    private final JwtTokenProvider tokenProvider;
    private final com.fidiacorp.credicasa.application.usecase.TwoFactorService twoFactor;
    private final com.fidiacorp.credicasa.application.usecase.TokenRevocationService revocations;

    public AuthController(AuthenticationManager authenticationManager, JwtTokenProvider tokenProvider, com.fidiacorp.credicasa.application.usecase.TwoFactorService twoFactor,
                          com.fidiacorp.credicasa.application.usecase.TokenRevocationService revocations) {
        this.authenticationManager = authenticationManager;
        this.tokenProvider = tokenProvider;
        this.twoFactor = twoFactor;
        this.revocations = revocations;
    }

    @PostMapping("/logout")
    @Operation(summary="Cerrar la sesión actual", description="Revoca únicamente el JWT Bearer presentado.")
    public ResponseEntity<Void> logout(java.security.Principal principal,
            @org.springframework.web.bind.annotation.RequestHeader("Authorization") String authorization) {
        String token=authorization.substring(7);
        revocations.revoke(java.util.UUID.fromString(principal.getName()), token,
            tokenProvider.getExpirationFromToken(token));
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/login")
    @Operation(summary = "Autenticar credenciales y generar token JWT",
            description = "Valida el correo y contraseña (BCrypt) y genera un token JWT con los roles correspondientes (ROLE_CLIENT o ROLE_REALTOR).")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Autenticación exitosa"),
            @ApiResponse(responseCode = "401", description = "Credenciales incorrectas")
    })
    public ResponseEntity<AuthResponseDto> login(@Valid @RequestBody AuthRequestDto request) {
        Authentication authentication = authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(request.email(), request.password())
        );

        UserDetails userDetails = (UserDetails) authentication.getPrincipal();
        twoFactor.verifyLogin(java.util.UUID.fromString(userDetails.getUsername()), request.otp());
        String token = tokenProvider.generateToken(userDetails);

        List<String> roles = userDetails.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .collect(Collectors.toList());

        return ResponseEntity.ok(new AuthResponseDto(
                token,
                "Bearer",
                request.email().trim().toLowerCase(java.util.Locale.ROOT),
                roles,
                tokenProvider.getExpirationMs()
        ));
    }
}
