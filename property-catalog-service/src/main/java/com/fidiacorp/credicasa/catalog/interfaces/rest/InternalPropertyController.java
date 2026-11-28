package com.fidiacorp.credicasa.catalog.interfaces.rest;

import com.fidiacorp.credicasa.catalog.application.dto.response.PropertySummaryResponse;
import com.fidiacorp.credicasa.catalog.application.usecase.PropertyService;
import io.swagger.v3.oas.annotations.Hidden;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

/** API interna (no enrutada por el gateway; X-Internal-Token): sustituye las FK físicas hacia {@code properties}. */
@Hidden
@RestController
@RequestMapping("/api/v1/internal/properties")
public class InternalPropertyController {
    private final PropertyService properties;

    public InternalPropertyController(PropertyService properties) {
        this.properties = properties;
    }

    @GetMapping("/{id}")
    public PropertySummaryResponse property(@PathVariable UUID id) {
        return properties.summary(id);
    }
}
