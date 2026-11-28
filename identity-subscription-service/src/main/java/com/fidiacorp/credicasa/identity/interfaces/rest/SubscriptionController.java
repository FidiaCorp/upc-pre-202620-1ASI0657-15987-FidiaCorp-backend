package com.fidiacorp.credicasa.identity.interfaces.rest;

import com.fidiacorp.credicasa.identity.application.dto.request.CheckoutRequest;
import com.fidiacorp.credicasa.identity.application.dto.request.VerifyPaymentRequest;
import com.fidiacorp.credicasa.identity.application.dto.response.CheckoutResponse;
import com.fidiacorp.credicasa.identity.application.dto.response.PaymentGatewayResponse;
import com.fidiacorp.credicasa.identity.application.dto.response.PaymentVerificationResponse;
import com.fidiacorp.credicasa.identity.application.dto.response.SubscriptionStatusResponse;
import com.fidiacorp.credicasa.identity.application.usecase.SubscriptionService;
import com.fidiacorp.credicasa.identity.domain.model.GatewayChargeStatus;
import jakarta.validation.Valid;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.security.Principal;
import java.util.List;
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

    private UUID id(Principal p) {
        return UUID.fromString(p.getName());
    }

    @GetMapping("/subscriptions/status")
    public ResponseEntity<SubscriptionStatusResponse> getSubscriptionStatus(Principal principal) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(subscriptionService.getSubscriptionStatus(id(principal)));
    }

    @GetMapping("/payments/gateways")
    public ResponseEntity<List<PaymentGatewayResponse>> listGateways() {
        return ResponseEntity.ok(subscriptionService.listGateways());
    }

    /** 200 si la pasarela aprueba; 402 Payment Required (status=REJECTED, retryAllowed=true) si rechaza. */
    @PostMapping("/payments/checkout")
    public ResponseEntity<CheckoutResponse> checkout(Principal principal, @Valid @RequestBody CheckoutRequest request) {
        CheckoutResponse response = subscriptionService.checkout(id(principal), request);
        HttpStatus status = response.status() == GatewayChargeStatus.APPROVED ? HttpStatus.OK : HttpStatus.PAYMENT_REQUIRED;
        return ResponseEntity.status(status).cacheControl(CacheControl.noStore()).body(response);
    }

    @PostMapping("/payments/verify")
    public ResponseEntity<PaymentVerificationResponse> verifyPayment(Principal principal, @Valid @RequestBody VerifyPaymentRequest request) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore())
                .body(subscriptionService.verifyPaymentAndActivateSubscription(id(principal), request));
    }
}
