package com.fidiacorp.credicasa.interfaces.rest;

import com.fidiacorp.credicasa.application.dto.request.VerifyPaymentRequest;
import com.fidiacorp.credicasa.application.dto.response.PaymentVerificationResponse;
import com.fidiacorp.credicasa.application.dto.response.SubscriptionStatusResponse;
import com.fidiacorp.credicasa.application.usecase.SubscriptionService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.security.Principal;
import java.util.UUID;

@RestController
@Validated
@RequestMapping("/api/v1")
@PreAuthorize("hasAnyRole('CLIENT','REALTOR')")
public class SubscriptionController {

    private final SubscriptionService subscriptionService;

    public SubscriptionController(SubscriptionService subscriptionService) {
        this.subscriptionService = subscriptionService;
    }

    @GetMapping("/subscriptions/status")
    public ResponseEntity<SubscriptionStatusResponse> getSubscriptionStatus(Principal principal) {
        UUID accountId = UUID.fromString(principal.getName());
        SubscriptionStatusResponse response = subscriptionService.getSubscriptionStatus(accountId);
        return ResponseEntity.ok(response);
    }

    @PostMapping("/payments/verify")
    public ResponseEntity<PaymentVerificationResponse> verifyPayment(
            Principal principal,
            @Valid @RequestBody VerifyPaymentRequest request) {
        UUID accountId = UUID.fromString(principal.getName());
        PaymentVerificationResponse response = subscriptionService.verifyPaymentAndActivateSubscription(accountId, request);
        return ResponseEntity.ok(response);
    }
}
