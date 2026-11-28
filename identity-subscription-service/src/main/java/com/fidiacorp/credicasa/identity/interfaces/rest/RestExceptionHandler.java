package com.fidiacorp.credicasa.identity.interfaces.rest;

import com.fidiacorp.credicasa.identity.domain.model.EmailAlreadyRegisteredException;
import com.fidiacorp.credicasa.identity.domain.model.PaymentRejectedException;
import com.fidiacorp.credicasa.identity.domain.model.TwoFactorException;
import com.fidiacorp.credicasa.shared.web.ProblemDetailExceptionHandler;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.time.LocalDateTime;
import java.util.Map;

/** RFC 7807 del shared kernel + errores propios del contexto de identidad (correo duplicado, pagos, TOTP). */
@RestControllerAdvice
public class RestExceptionHandler extends ProblemDetailExceptionHandler {

    @Schema(name = "ErrorResponse", description = "RFC 7807 Problem Details con extensiones de compatibilidad.")
    public record ErrorResponse(String type, String title, int status, String detail, String instance,
                                String error, String message, LocalDateTime timestamp, Map<String, String> validationErrors,
                                String correlationId) {}

    @Schema(name = "TwoFactorErrorResponse", description = "RFC 7807 Problem Details del segundo factor, con código estable.")
    public record TwoFactorErrorResponse(String type, String title, int status, String detail, String instance,
                                         String error, String message, LocalDateTime timestamp, Map<String, String> validationErrors,
                                         String code) {}

    @ExceptionHandler(PaymentRejectedException.class)
    public ResponseEntity<ProblemDetail> handlePaymentRejected(PaymentRejectedException ex, HttpServletRequest request) {
        return respond(HttpStatus.UNPROCESSABLE_ENTITY, ex.getMessage(), request);
    }

    @ExceptionHandler(EmailAlreadyRegisteredException.class)
    public ResponseEntity<ProblemDetail> handleDuplicateEmail(EmailAlreadyRegisteredException ex, HttpServletRequest request) {
        ProblemDetail problem = problem(HttpStatus.CONFLICT, ex.getMessage(), request);
        problem.setProperty("validationErrors", Map.of("email", ex.getMessage()));
        return ResponseEntity.status(HttpStatus.CONFLICT).body(problem);
    }

    @ExceptionHandler(TwoFactorException.class)
    public ResponseEntity<ProblemDetail> handleTwoFactor(TwoFactorException ex, HttpServletRequest request) {
        HttpStatus status = HttpStatus.valueOf(ex.status());
        ProblemDetail problem = problem(status, ex.getMessage(), request);
        problem.setProperty("code", ex.code());
        var response = ResponseEntity.status(status);
        if (ex.retryAfterSeconds() > 0) response.header("Retry-After", Long.toString(ex.retryAfterSeconds()));
        return response.body(problem);
    }
}
