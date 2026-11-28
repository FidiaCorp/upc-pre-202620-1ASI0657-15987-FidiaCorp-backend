package com.fidiacorp.credicasa.catalog.interfaces.rest;

import com.fidiacorp.credicasa.shared.web.ProblemDetailExceptionHandler;
import io.swagger.v3.oas.annotations.media.Schema;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.time.LocalDateTime;
import java.util.Map;

/** RFC 7807 del shared kernel; las excepciones de dominio del catálogo extienden las del kernel (404/422). */
@RestControllerAdvice
public class RestExceptionHandler extends ProblemDetailExceptionHandler {
    @Schema(name = "ErrorResponse", description = "RFC 7807 Problem Details con extensiones de compatibilidad.")
    public record ErrorResponse(String type, String title, int status, String detail, String instance,
                                String error, String message, LocalDateTime timestamp, Map<String, String> validationErrors,
                                String correlationId) {}
}
