package com.fidiacorp.credicasa.interfaces.rest;
import com.fidiacorp.credicasa.application.dto.response.*;
import com.fidiacorp.credicasa.application.usecase.OfferNotificationService;
import jakarta.validation.constraints.*;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import org.springframework.http.*;
import java.security.Principal;
import java.util.UUID;
@RestController @Validated @RequestMapping("/api/v1/notifications")
@PreAuthorize("hasAnyRole('CLIENT','REALTOR')")
public class OfferNotificationController {
    private final OfferNotificationService service;
    public OfferNotificationController(OfferNotificationService service) {this.service=service;}
    private UUID id(Principal p) {return UUID.fromString(p.getName());}
    @GetMapping public ResponseEntity<NotificationPageResponse> list(Principal p,
        @RequestParam(defaultValue="false") boolean unreadOnly,
        @RequestParam(defaultValue="0") @Min(0) int page,@RequestParam(defaultValue="20") @Min(1) @Max(100) int size) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(service.list(id(p),unreadOnly,page,size));
    }
    @PatchMapping("/{notificationId}/read")
    public ResponseEntity<OfferNotificationResponse> markRead(Principal p,@PathVariable UUID notificationId) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(service.markRead(id(p),notificationId));
    }
}
