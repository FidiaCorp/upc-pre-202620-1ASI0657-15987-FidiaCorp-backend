package com.fidiacorp.credicasa.communication.interfaces.rest;

import com.fidiacorp.credicasa.shared.web.ProblemDetailExceptionHandler;
import io.github.resilience4j.circuitbreaker.CallNotPermittedException;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.time.LocalDateTime;
import java.util.Map;

/** RFC 7807 del shared kernel + circuito abierto hacia otro microservicio → 503 con Retry-After. */
@RestControllerAdvice
public class RestExceptionHandler extends ProblemDetailExceptionHandler {
    @Schema(name = "ErrorResponse", description = "RFC 7807 Problem Details con extensiones de compatibilidad.")
    public record ErrorResponse(String type, String title, int status, String detail, String instance,
                                String error, String message, LocalDateTime timestamp, Map<String, String> validationErrors,
                                String correlationId) {}

    @ExceptionHandler(CallNotPermittedException.class)
    public ResponseEntity<ProblemDetail> handleOpenCircuit(CallNotPermittedException ex, HttpServletRequest request) {
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).header("Retry-After", "15")
                .body(problem(HttpStatus.SERVICE_UNAVAILABLE, "Servicio dependiente temporalmente no disponible (circuito abierto).", request));
    }
}
