package com.fidiacorp.credicasa.interfaces.rest;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

@RestControllerAdvice
public class RestExceptionHandler {

    public record ErrorResponse(
            int status,
            String error,
            String message,
            LocalDateTime timestamp,
            Map<String, String> validationErrors
    ) {}

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponse> handleValidationExceptions(MethodArgumentNotValidException ex) {
        Map<String, String> errors = new HashMap<>();
        for (FieldError error : ex.getBindingResult().getFieldErrors()) {
            errors.put(error.getField(), error.getDefaultMessage());
        }

        ErrorResponse response = new ErrorResponse(
                HttpStatus.BAD_REQUEST.value(),
                "Bad Request",
                "Error en la validación de los datos enviados.",
                LocalDateTime.now(),
                errors
        );
        return ResponseEntity.badRequest().body(response);
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ErrorResponse> handleIllegalArgument(IllegalArgumentException ex) {
        ErrorResponse response = new ErrorResponse(
                HttpStatus.BAD_REQUEST.value(),
                "Bad Request",
                ex.getMessage(),
                LocalDateTime.now(),
                null
        );
        return ResponseEntity.badRequest().body(response);
    }

    @ExceptionHandler(BadCredentialsException.class)
    public ResponseEntity<ErrorResponse> handleBadCredentials(BadCredentialsException ex) {
        ErrorResponse response = new ErrorResponse(
                HttpStatus.UNAUTHORIZED.value(),
                "Unauthorized",
                "Credenciales inválidas. Verifique su correo y contraseña.",
                LocalDateTime.now(),
                null
        );
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(response);
    }

    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ErrorResponse> handleAccessDenied(AccessDeniedException ex) {
        ErrorResponse response = new ErrorResponse(
                HttpStatus.FORBIDDEN.value(),
                "Forbidden",
                "No cuenta con los privilegios suficientes para ejecutar esta operación.",
                LocalDateTime.now(),
                null
        );
        return ResponseEntity.status(HttpStatus.FORBIDDEN).body(response);
    }

    @ExceptionHandler(com.fidiacorp.credicasa.domain.model.EmailAlreadyRegisteredException.class)
    public ResponseEntity<ErrorResponse> handleDuplicateEmail(RuntimeException ex) {
        return ResponseEntity.status(HttpStatus.CONFLICT).body(new ErrorResponse(409, "Conflict",
            ex.getMessage(), LocalDateTime.now(), Map.of("email", ex.getMessage())));
    }

    @ExceptionHandler(org.springframework.http.converter.HttpMessageNotReadableException.class)
    public ResponseEntity<ErrorResponse> handleMalformedBody(Exception ex) {
        return ResponseEntity.badRequest().body(new ErrorResponse(400, "Bad Request",
            "El cuerpo de la solicitud no es válido.", LocalDateTime.now(), null));
    }

    @ExceptionHandler(org.springframework.orm.ObjectOptimisticLockingFailureException.class)
    public ResponseEntity<ErrorResponse> handleConcurrentUpdate(Exception ex) {
        return ResponseEntity.status(HttpStatus.CONFLICT).body(new ErrorResponse(409, "Conflict",
            "El perfil cambió. Vuelva a cargarlo e intente otra vez.", LocalDateTime.now(), null));
    }

    public record TwoFactorErrorResponse(int status, String error, String message,
            LocalDateTime timestamp, Map<String,String> validationErrors, String code) {}

    @ExceptionHandler(com.fidiacorp.credicasa.domain.model.TwoFactorException.class)
    public ResponseEntity<TwoFactorErrorResponse> handleTwoFactor(com.fidiacorp.credicasa.domain.model.TwoFactorException ex) {
        var response = ResponseEntity.status(ex.status());
        if(ex.retryAfterSeconds()>0) response.header("Retry-After", Long.toString(ex.retryAfterSeconds()));
        return response.body(new TwoFactorErrorResponse(ex.status(), HttpStatus.valueOf(ex.status()).getReasonPhrase(),
            ex.getMessage(), LocalDateTime.now(), null, ex.code()));
    }

    @ExceptionHandler(com.fidiacorp.credicasa.domain.model.PropertyNotFoundException.class)
    public ResponseEntity<ErrorResponse> handlePropertyNotFound(RuntimeException ex) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(new ErrorResponse(404,"Not Found",
            ex.getMessage(),LocalDateTime.now(),null));
    }

    @ExceptionHandler(jakarta.validation.ConstraintViolationException.class)
    public ResponseEntity<ErrorResponse> handleQueryValidation(jakarta.validation.ConstraintViolationException ex) {
        Map<String,String> errors=new HashMap<>();
        ex.getConstraintViolations().forEach(v -> errors.put(v.getPropertyPath().toString(),v.getMessage()));
        return ResponseEntity.badRequest().body(new ErrorResponse(400,"Bad Request",
            "Parámetros de consulta inválidos.",LocalDateTime.now(),errors));
    }

    @ExceptionHandler(org.springframework.web.method.annotation.MethodArgumentTypeMismatchException.class)
    public ResponseEntity<ErrorResponse> handleInvalidQueryType(Exception ex) {
        return ResponseEntity.badRequest().body(new ErrorResponse(400,"Bad Request",
            "El formato de un parámetro no es válido.",LocalDateTime.now(),null));
    }

    @ExceptionHandler(org.springframework.web.HttpRequestMethodNotSupportedException.class)
    public ResponseEntity<ErrorResponse> handleUnsupportedMethod(org.springframework.web.HttpRequestMethodNotSupportedException ex) {
        var response=ResponseEntity.status(HttpStatus.METHOD_NOT_ALLOWED);
        if(ex.getSupportedMethods()!=null)response.header("Allow",String.join(", ",ex.getSupportedMethods()));
        return response.body(new ErrorResponse(405,"Method Not Allowed",
            "La operación solicitada no está disponible.",LocalDateTime.now(),null));
    }

    @ExceptionHandler(com.fidiacorp.credicasa.domain.model.OfferResourceNotFoundException.class)
    public ResponseEntity<ErrorResponse> handleOfferResourceNotFound(RuntimeException ex) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(new ErrorResponse(404,"Not Found",
            ex.getMessage(),LocalDateTime.now(),null));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleGeneralException(Exception ex) {
        ErrorResponse response = new ErrorResponse(
                HttpStatus.INTERNAL_SERVER_ERROR.value(),
                "Internal Server Error",
                "No se pudo completar la solicitud.",
                LocalDateTime.now(),
                null
        );
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(response);
    }
}
