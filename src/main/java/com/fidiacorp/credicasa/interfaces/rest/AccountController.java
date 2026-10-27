package com.fidiacorp.credicasa.interfaces.rest;
import com.fidiacorp.credicasa.application.dto.request.RegisterAccountRequest;
import com.fidiacorp.credicasa.application.dto.request.UpdateProfileRequest;
import com.fidiacorp.credicasa.application.dto.response.AccountProfileResponse;
import com.fidiacorp.credicasa.application.usecase.AccountService;
import jakarta.validation.Valid;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import java.security.Principal;
import java.util.UUID;
@RestController
@RequestMapping("/api/v1")
public class AccountController {
    private final AccountService accounts;
    public AccountController(AccountService accounts) { this.accounts = accounts; }
    @PostMapping("/auth/register")
    public ResponseEntity<AccountProfileResponse> register(@Valid @RequestBody RegisterAccountRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(accounts.register(request));
    }
    @GetMapping("/users/me")
    public AccountProfileResponse getProfile(Principal principal) {
        return accounts.getProfile(UUID.fromString(principal.getName()));
    }
    @PutMapping("/users/me")
    public AccountProfileResponse updateProfile(Principal principal, @Valid @RequestBody UpdateProfileRequest request) {
        return accounts.updateProfile(UUID.fromString(principal.getName()), request);
    }
}
