package com.fidiacorp.credicasa.interfaces.rest;
import com.fidiacorp.credicasa.application.dto.request.CreateCreditOfferRequest;
import com.fidiacorp.credicasa.application.dto.response.*;
import com.fidiacorp.credicasa.application.usecase.CreditOfferService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import org.springframework.http.*;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import java.security.Principal;
import java.util.UUID;
@RestController @Validated @RequestMapping("/api/v1/offers")
@PreAuthorize("hasAnyRole('CLIENT','REALTOR')")
public class CreditOfferController {
    private final CreditOfferService service;
    public CreditOfferController(CreditOfferService service) {this.service=service;}
    private UUID id(Principal p) {return UUID.fromString(p.getName());}
    @PostMapping @PreAuthorize("hasRole('REALTOR')")
    public ResponseEntity<CreditOfferResponse> issue(Principal p,@Valid @RequestBody CreateCreditOfferRequest request) {
        CreditOfferResponse response=service.issue(id(p),request);
        return ResponseEntity.created(java.net.URI.create("/api/v1/offers/"+response.id())).body(response);
    }
    @GetMapping public ResponseEntity<CreditOfferPageResponse> list(Principal p,
        @RequestParam(defaultValue="0") @Min(0) int page,@RequestParam(defaultValue="20") @Min(1) @Max(100) int size) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(service.listReceived(id(p),page,size));
    }
    @GetMapping("/{offerId}") public ResponseEntity<CreditOfferResponse> get(Principal p,@PathVariable UUID offerId) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(service.getReceived(id(p),offerId));
    }
}
