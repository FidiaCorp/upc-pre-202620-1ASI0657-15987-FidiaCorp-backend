package com.fidiacorp.credicasa.interfaces.rest;
import com.fidiacorp.credicasa.application.dto.request.CreatePropertyRequest;
import com.fidiacorp.credicasa.application.dto.response.*;
import com.fidiacorp.credicasa.application.usecase.PropertyService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import org.springframework.http.*;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import java.security.Principal;
import java.util.*;
@RestController @Validated
@RequestMapping("/api/v1/properties")
@PreAuthorize("hasAnyRole('CLIENT','REALTOR')")
public class PropertyController {
    private final PropertyService service;
    public PropertyController(PropertyService service) {this.service=service;}
    @PostMapping
    public ResponseEntity<PropertyResponse> create(Principal principal,@Valid @RequestBody CreatePropertyRequest request) {
        PropertyResponse response=service.create(UUID.fromString(principal.getName()),request);
        return ResponseEntity.created(java.net.URI.create("/api/v1/properties/"+response.id())).body(response);
    }
    @GetMapping
    public PropertyPageResponse search(Principal principal,
        @RequestParam(defaultValue="") @Size(max=120) String q,
        @RequestParam(required=false) @Size(max=20) List<@NotBlank @Size(max=40) String> tags,
        @RequestParam(defaultValue="false") boolean mine,
        @RequestParam(defaultValue="0") @Min(0) int page,
        @RequestParam(defaultValue="20") @Min(1) @Max(100) int size) {
        return service.search(UUID.fromString(principal.getName()),q,tags,mine,page,size);
    }
    @GetMapping("/{id}") public PropertyResponse get(@PathVariable UUID id) {return service.get(id);}
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(Principal principal,@PathVariable UUID id) {
        service.delete(UUID.fromString(principal.getName()),id);return ResponseEntity.noContent().build();
    }
}
