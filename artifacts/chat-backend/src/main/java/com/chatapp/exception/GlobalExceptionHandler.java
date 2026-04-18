package com.chatapp.exception;

import com.chatapp.dto.response.ApiErrorResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.ConstraintViolationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

/**
 * Global exception handler for all REST API errors.
 *
 * @RestControllerAdvice intercepts all exceptions thrown from @RestController methods
 * and maps them to structured JSON error responses instead of Spring's default HTML errors.
 *
 * Follows the SOLID Open/Closed Principle: add new exception handlers without modifying existing ones.
 *
 * Error categories handled:
 * - 400 Bad Request: validation failures, business rule violations
 * - 401 Unauthorized: missing or invalid JWT
 * - 403 Forbidden: valid JWT but insufficient permissions
 * - 404 Not Found: resource doesn't exist
 * - 409 Conflict: duplicate entity (username/email already taken)
 * - 500 Internal Server Error: unexpected exceptions
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger logger = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    /**
     * Handle bean validation errors from @Valid on request bodies.
     * Returns a map of field name → error message.
     */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiErrorResponse> handleValidationErrors(
        MethodArgumentNotValidException ex,
        HttpServletRequest request
    ) {
        Map<String, String> details = new HashMap<>();
        ex.getBindingResult().getAllErrors().forEach(error -> {
            String fieldName = ((FieldError) error).getField();
            String message = error.getDefaultMessage();
            details.put(fieldName, message);
        });

        logger.warn("Validation failed for request to {}: {}", request.getRequestURI(), details);

        return ResponseEntity.badRequest().body(ApiErrorResponse.builder()
            .timestamp(LocalDateTime.now())
            .status(HttpStatus.BAD_REQUEST.value())
            .error("Validation Failed")
            .message("Request contains invalid fields")
            .path(request.getRequestURI())
            .details(details)
            .build());
    }

    /**
     * Handle constraint violations from @Validated on method parameters.
     */
    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<ApiErrorResponse> handleConstraintViolation(
        ConstraintViolationException ex,
        HttpServletRequest request
    ) {
        logger.warn("Constraint violation: {}", ex.getMessage());
        return ResponseEntity.badRequest().body(ApiErrorResponse.builder()
            .timestamp(LocalDateTime.now())
            .status(HttpStatus.BAD_REQUEST.value())
            .error("Constraint Violation")
            .message(ex.getMessage())
            .path(request.getRequestURI())
            .build());
    }

    /**
     * Handle ResourceNotFoundException — resource not found in DB.
     */
    @ExceptionHandler(ResourceNotFoundException.class)
    public ResponseEntity<ApiErrorResponse> handleNotFound(
        ResourceNotFoundException ex,
        HttpServletRequest request
    ) {
        logger.warn("Resource not found: {}", ex.getMessage());
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(ApiErrorResponse.builder()
            .timestamp(LocalDateTime.now())
            .status(HttpStatus.NOT_FOUND.value())
            .error("Not Found")
            .message(ex.getMessage())
            .path(request.getRequestURI())
            .build());
    }

    /**
     * Handle DuplicateResourceException — e.g., username/email already taken.
     */
    @ExceptionHandler(DuplicateResourceException.class)
    public ResponseEntity<ApiErrorResponse> handleDuplicate(
        DuplicateResourceException ex,
        HttpServletRequest request
    ) {
        logger.warn("Duplicate resource: {}", ex.getMessage());
        return ResponseEntity.status(HttpStatus.CONFLICT).body(ApiErrorResponse.builder()
            .timestamp(LocalDateTime.now())
            .status(HttpStatus.CONFLICT.value())
            .error("Conflict")
            .message(ex.getMessage())
            .path(request.getRequestURI())
            .build());
    }

    /**
     * Handle UnauthorizedException — custom auth failures.
     */
    @ExceptionHandler(UnauthorizedException.class)
    public ResponseEntity<ApiErrorResponse> handleUnauthorized(
        UnauthorizedException ex,
        HttpServletRequest request
    ) {
        logger.warn("Unauthorized: {}", ex.getMessage());
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(ApiErrorResponse.builder()
            .timestamp(LocalDateTime.now())
            .status(HttpStatus.UNAUTHORIZED.value())
            .error("Unauthorized")
            .message(ex.getMessage())
            .path(request.getRequestURI())
            .build());
    }

    /**
     * Handle Spring Security authentication failures (invalid JWT, etc.).
     */
    @ExceptionHandler(AuthenticationException.class)
    public ResponseEntity<ApiErrorResponse> handleAuthenticationException(
        AuthenticationException ex,
        HttpServletRequest request
    ) {
        logger.warn("Authentication failure: {}", ex.getMessage());
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(ApiErrorResponse.builder()
            .timestamp(LocalDateTime.now())
            .status(HttpStatus.UNAUTHORIZED.value())
            .error("Authentication Failed")
            .message("Invalid credentials")
            .path(request.getRequestURI())
            .build());
    }

    /**
     * Handle Spring Security access denied (valid JWT but wrong permissions).
     */
    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ApiErrorResponse> handleAccessDenied(
        AccessDeniedException ex,
        HttpServletRequest request
    ) {
        logger.warn("Access denied to {} by user", request.getRequestURI());
        return ResponseEntity.status(HttpStatus.FORBIDDEN).body(ApiErrorResponse.builder()
            .timestamp(LocalDateTime.now())
            .status(HttpStatus.FORBIDDEN.value())
            .error("Forbidden")
            .message("You don't have permission to perform this action")
            .path(request.getRequestURI())
            .build());
    }

    /**
     * Catch-all for unexpected exceptions.
     * Log the full stack trace but return a generic message to the client
     * (never expose internal error details to clients — security risk).
     */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiErrorResponse> handleGenericException(
        Exception ex,
        HttpServletRequest request
    ) {
        logger.error("Unexpected error at {}: {}", request.getRequestURI(), ex.getMessage(), ex);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(ApiErrorResponse.builder()
            .timestamp(LocalDateTime.now())
            .status(HttpStatus.INTERNAL_SERVER_ERROR.value())
            .error("Internal Server Error")
            .message("An unexpected error occurred. Please try again later.")
            .path(request.getRequestURI())
            .build());
    }
}
