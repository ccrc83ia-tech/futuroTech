package com.futurotech.legal.infrastructure.adapter.in.web;

import java.time.Instant;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.ServletWebRequest;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.servlet.NoHandlerFoundException;

import com.futurotech.legal.domain.exception.AuthenticationException;
import com.futurotech.legal.domain.exception.DocumentGenerationException;
import com.futurotech.legal.domain.exception.InvalidSchemaException;
import com.futurotech.legal.domain.exception.ResourceNotFoundException;
import com.futurotech.legal.domain.exception.TenantNotFoundException;
import com.futurotech.legal.infrastructure.adapter.in.web.dto.ApiError;
import com.futurotech.legal.infrastructure.config.CorrelationIdFilter;

/**
 * Traduce las excepciones a respuestas HTTP con un cuerpo {@link ApiError}
 * consistente (en español) e incluye el {@code traceId} de la petición para
 * poder cruzar el error reportado con los logs del servidor.
 *
 * <p>Política de logging: los errores esperados del cliente (4xx) se registran
 * en nivel WARN sin traza (son parte del flujo normal); los fallos del servidor
 * (5xx) y las excepciones no previstas se registran en ERROR con la traza
 * completa para su diagnóstico.</p>
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    // --- 401 ---
    @ExceptionHandler(AuthenticationException.class)
    public ResponseEntity<ApiError> handleAuth(AuthenticationException e, WebRequest request) {
        log.warn("Autenticación fallida: {}", e.getMessage());
        return build(HttpStatus.UNAUTHORIZED, e.getMessage(), request);
    }

    // --- 403 (fallos de @PreAuthorize / autorización) ---
    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ApiError> handleAccessDenied(AccessDeniedException e, WebRequest request) {
        log.warn("Acceso denegado en {}: {}", path(request), e.getMessage());
        return build(HttpStatus.FORBIDDEN, "No tiene permisos para realizar esta acción", request);
    }

    // --- 404 ---
    @ExceptionHandler({TenantNotFoundException.class, ResourceNotFoundException.class})
    public ResponseEntity<ApiError> handleNotFound(RuntimeException e, WebRequest request) {
        log.warn("Recurso no encontrado: {}", e.getMessage());
        return build(HttpStatus.NOT_FOUND, e.getMessage(), request);
    }

    // --- 405 ---
    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public ResponseEntity<ApiError> handleMethodNotSupported(
            HttpRequestMethodNotSupportedException e, WebRequest request) {
        log.warn("Método no soportado en {}: {}", path(request), e.getMessage());
        return build(HttpStatus.METHOD_NOT_ALLOWED, "Método HTTP no soportado para este recurso", request);
    }

    // --- 404 (ruta inexistente) ---
    @ExceptionHandler(NoHandlerFoundException.class)
    public ResponseEntity<ApiError> handleNoHandler(NoHandlerFoundException e, WebRequest request) {
        log.warn("Ruta no encontrada: {} {}", e.getHttpMethod(), e.getRequestURL());
        return build(HttpStatus.NOT_FOUND, "Recurso no encontrado", request);
    }

    // --- 422 (reglas de negocio / validación de esquema) ---
    @ExceptionHandler(InvalidSchemaException.class)
    public ResponseEntity<ApiError> handleInvalidSchema(InvalidSchemaException e, WebRequest request) {
        log.warn("Esquema inválido: {}", e.getMessage());
        return build(HttpStatus.UNPROCESSABLE_CONTENT, e.getMessage(), request);
    }

    // --- 422 (validación de DTOs @Valid) ---
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiError> handleValidation(MethodArgumentNotValidException e, WebRequest request) {
        String msg = e.getBindingResult().getFieldErrors().stream()
                .findFirst()
                .map(fe -> fe.getField() + ": " + fe.getDefaultMessage())
                .orElse("Datos inválidos");
        log.warn("Validación fallida en {}: {}", path(request), msg);
        return build(HttpStatus.UNPROCESSABLE_CONTENT, msg, request);
    }

    // --- 400 (cuerpo JSON ilegible / mal formado) ---
    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ApiError> handleUnreadable(HttpMessageNotReadableException e, WebRequest request) {
        log.warn("Cuerpo de la petición ilegible en {}: {}", path(request), e.getMostSpecificCause().getMessage());
        return build(HttpStatus.BAD_REQUEST, "El cuerpo de la petición es inválido o está mal formado", request);
    }

    // --- 400 (argumentos ilegales explícitos del dominio) ---
    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ApiError> handleIllegalArgument(IllegalArgumentException e, WebRequest request) {
        log.warn("Argumento inválido en {}: {}", path(request), e.getMessage());
        return build(HttpStatus.BAD_REQUEST, e.getMessage(), request);
    }

    // --- 500 (fallo en la generación: IA, exportación, etc.) ---
    @ExceptionHandler(DocumentGenerationException.class)
    public ResponseEntity<ApiError> handleGeneration(DocumentGenerationException e, WebRequest request) {
        log.error("Falla de generación de documento en {}", path(request), e);
        return build(HttpStatus.INTERNAL_SERVER_ERROR, e.getMessage(), request);
    }

    // --- 500 (red de seguridad: todo lo no previsto) ---
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiError> handleUnexpected(Exception e, WebRequest request) {
        log.error("Error no controlado en {}", path(request), e);
        // No exponer detalles internos al cliente; el traceId permite ubicar la traza.
        return build(HttpStatus.INTERNAL_SERVER_ERROR,
                "Ha ocurrido un error inesperado. Contacte a soporte con el identificador de seguimiento.",
                request);
    }

    private ResponseEntity<ApiError> build(HttpStatus status, String message, WebRequest request) {
        ApiError body = new ApiError(
                Instant.now(),
                status.value(),
                status.getReasonPhrase(),
                message,
                path(request),
                MDC.get(CorrelationIdFilter.MDC_KEY));
        return ResponseEntity.status(status).body(body);
    }

    private String path(WebRequest request) {
        if (request instanceof ServletWebRequest swr) {
            return swr.getRequest().getRequestURI();
        }
        return request.getDescription(false).replaceFirst("^uri=", "");
    }
}
