package com.fidiacorp.credicasa.interfaces.rest;
import com.fidiacorp.credicasa.application.usecase.TwoFactorService;
import com.fidiacorp.credicasa.application.dto.request.*;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;
import org.springframework.http.ResponseEntity;
import java.security.Principal;
import java.util.UUID;
@RestController
@RequestMapping("/api/v1/users/me/two-factor")
public class TwoFactorController {
    private final TwoFactorService service;
    public TwoFactorController(TwoFactorService service) {this.service=service;}
    private UUID id(Principal p) {return UUID.fromString(p.getName());}
    @GetMapping public TwoFactorService.Status status(Principal p) {return service.status(id(p));}
    @PostMapping("/setup")
    public ResponseEntity<TwoFactorService.Setup> setup(Principal p,@Valid @RequestBody TwoFactorSetupRequest r) {
        return ResponseEntity.ok().cacheControl(org.springframework.http.CacheControl.noStore()).body(service.setup(id(p),r.password()));
    }
    @PostMapping("/confirm")
    public TwoFactorService.Status confirm(Principal p,@Valid @RequestBody TwoFactorCodeRequest r) {
        return service.confirm(id(p),r.otp());
    }
    @PostMapping("/disable")
    public TwoFactorService.Status disable(Principal p,@Valid @RequestBody TwoFactorDisableRequest r) {
        return service.disable(id(p),r.password(),r.otp());
    }
}
