package com.fidiacorp.credicasa.shared.web;

import com.fidiacorp.credicasa.shared.error.BusinessRuleViolationException;
import com.fidiacorp.credicasa.shared.error.ResourceNotFoundException;
import com.fidiacorp.credicasa.shared.error.UpstreamServiceException;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.validation.FieldError;
import org.springframework.web.ErrorResponse;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

import java.net.URI;
import java.time.DateTimeException;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

/**
 * Manejador RFC 7807 común a todos los microservicios. Cada servicio lo extiende con un {@code @RestControllerAdvice}
 * propio para añadir sus excepciones de dominio. Conserva las extensiones históricas {@code error, message, timestamp,
 * validationErrors} que consume el FrontEnd, y añade {@code correlationId} para trazabilidad.
 */
public class ProblemDetailExceptionHandler {
    private static final Logger log = LoggerFactory.getLogger(ProblemDetailExceptionHandler.class);
    public static final String TYPE_BASE = "https://api.credicasa.pe/problems/";

    public static ProblemDetail problem(HttpStatus status, String detail, HttpServletRequest request) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(status, detail);
        problem.setType(URI.create(TYPE_BASE + status.getReasonPhrase().toLowerCase(Locale.ROOT).replace(' ', '-')));
        problem.setTitle(status.getReasonPhrase());
        if (request != null) {
            problem.setInstance(URI.create(request.getRequestURI()));
            Object correlationId = request.getAttribute(CorrelationIdFilter.REQUEST_ATTRIBUTE);
            if (correlationId != null) problem.setProperty("correlationId", correlationId);
        }
        problem.setProperty("error", status.getReasonPhrase());
        problem.setProperty("message", detail);
        problem.setProperty("timestamp", LocalDateTime.now());
        return problem;
    }

    protected ResponseEntity<ProblemDetail> respond(HttpStatus status, String detail, HttpServletRequest request) {
        return ResponseEntity.status(status).body(problem(status, detail, request));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ProblemDetail> handleValidation(MethodArgumentNotValidException ex, HttpServletRequest request) {
        Map<String, String> errors = new LinkedHashMap<>();
        for (FieldError error : ex.getBindingResult().getFieldErrors()) {
            errors.putIfAbsent(error.getField(), error.getDefaultMessage());
        }
        ProblemDetail problem = problem(HttpStatus.BAD_REQUEST, "Error en la validación de los datos enviados.", request);
        problem.setProperty("validationErrors", errors);
        return ResponseEntity.badRequest().body(problem);
    }

    @ExceptionHandler(jakarta.validation.ConstraintViolationException.class)
    public ResponseEntity<ProblemDetail> handleConstraintViolation(jakarta.validation.ConstraintViolationException ex,
                                                                   HttpServletRequest request) {
        Map<String, String> errors = new LinkedHashMap<>();
        ex.getConstraintViolations().forEach(v -> errors.put(v.getPropertyPath().toString(), v.getMessage()));
        ProblemDetail problem = problem(HttpStatus.BAD_REQUEST, "Parámetros de consulta inválidos.", request);
        problem.setProperty("validationErrors", errors);
        return ResponseEntity.badRequest().body(problem);
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ProblemDetail> handleIllegalArgument(IllegalArgumentException ex, HttpServletRequest request) {
        return respond(HttpStatus.BAD_REQUEST, ex.getMessage(), request);
    }

    @ExceptionHandler(IllegalStateException.class)
    public ResponseEntity<ProblemDetail> handleIllegalState(IllegalStateException ex, HttpServletRequest request) {
        return respond(HttpStatus.CONFLICT, ex.getMessage(), request);
    }

    @ExceptionHandler(BadCredentialsException.class)
    public ResponseEntity<ProblemDetail> handleBadCredentials(BadCredentialsException ex, HttpServletRequest request) {
        return respond(HttpStatus.UNAUTHORIZED, "Credenciales inválidas. Verifique su correo y contraseña.", request);
    }

    @ExceptionHandler({AccessDeniedException.class, SecurityException.class})
    public ResponseEntity<ProblemDetail> handleAccessDenied(RuntimeException ex, HttpServletRequest request) {
        return respond(HttpStatus.FORBIDDEN, "No cuenta con los privilegios suficientes para ejecutar esta operación.", request);
    }

    @ExceptionHandler(ResourceNotFoundException.class)
    public ResponseEntity<ProblemDetail> handleNotFound(ResourceNotFoundException ex, HttpServletRequest request) {
        return respond(HttpStatus.NOT_FOUND, ex.getMessage(), request);
    }

    @ExceptionHandler(BusinessRuleViolationException.class)
    public ResponseEntity<ProblemDetail> handleBusinessRule(BusinessRuleViolationException ex, HttpServletRequest request) {
        return respond(HttpStatus.UNPROCESSABLE_ENTITY, ex.getMessage(), request);
    }

    @ExceptionHandler(UpstreamServiceException.class)
    public ResponseEntity<ProblemDetail> handleUpstream(UpstreamServiceException ex, HttpServletRequest request) {
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).header("Retry-After", "5")
                .body(problem(HttpStatus.SERVICE_UNAVAILABLE, ex.getMessage(), request));
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ProblemDetail> handleMalformedBody(HttpMessageNotReadableException ex, HttpServletRequest request) {
        return respond(HttpStatus.BAD_REQUEST, "El cuerpo de la solicitud no es válido.", request);
    }

    @ExceptionHandler(OptimisticLockingFailureException.class)
    public ResponseEntity<ProblemDetail> handleConcurrentUpdate(OptimisticLockingFailureException ex, HttpServletRequest request) {
        return respond(HttpStatus.CONFLICT, "El recurso cambió. Vuelva a cargarlo e intente otra vez.", request);
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<ProblemDetail> handleTypeMismatch(MethodArgumentTypeMismatchException ex, HttpServletRequest request) {
        return respond(HttpStatus.BAD_REQUEST, "El formato de un parámetro no es válido.", request);
    }

    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public ResponseEntity<ProblemDetail> handleUnsupportedMethod(HttpRequestMethodNotSupportedException ex, HttpServletRequest request) {
        var response = ResponseEntity.status(HttpStatus.METHOD_NOT_ALLOWED);
        if (ex.getSupportedMethods() != null) {
            response.header("Allow", String.join(", ", ex.getSupportedMethods()));
        }
        return response.body(problem(HttpStatus.METHOD_NOT_ALLOWED, "La operación solicitada no está disponible.", request));
    }

    @ExceptionHandler(DateTimeException.class)
    public ResponseEntity<ProblemDetail> handleDateRange(DateTimeException ex, HttpServletRequest request) {
        return respond(HttpStatus.BAD_REQUEST, "La fecha queda fuera del rango permitido.", request);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ProblemDetail> handleGeneral(Exception ex, HttpServletRequest request) {
        if (ex instanceof ErrorResponse framework && framework.getStatusCode().is4xxClientError()) {
            HttpStatus status = HttpStatus.valueOf(framework.getStatusCode().value());
            return respond(status, status == HttpStatus.NOT_FOUND ? "El recurso solicitado no existe." : "La solicitud no es válida.", request);
        }
        log.error("Error no controlado en {}", request == null ? "?" : request.getRequestURI(), ex);
        return respond(HttpStatus.INTERNAL_SERVER_ERROR, "No se pudo completar la solicitud.", request);
    }
}
