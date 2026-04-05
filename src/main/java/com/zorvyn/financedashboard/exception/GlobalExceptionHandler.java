package com.zorvyn.financedashboard.exception;

import com.zorvyn.financedashboard.dto.response.ApiErrorResponse;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.time.Instant;
import java.util.stream.Collectors;

/**
 * ============================================================================
 * GLOBAL EXCEPTION HANDLER — Centralised Error Response Factory
 * ============================================================================
 *
 * @RestControllerAdvice intercepts ALL exceptions thrown from controllers
 * and converts them into our standardised ApiErrorResponse JSON format.
 *
 * Why centralise exception handling?
 *
 *   1. CONSISTENCY: Every error, regardless of which controller throws it,
 *      produces the same JSON structure. The frontend can rely on a single
 *      error schema.
 *
 *   2. SEPARATION OF CONCERNS: Controllers focus on happy-path logic.
 *      They throw domain exceptions (ResourceNotFoundException, etc.)
 *      and this handler converts them to HTTP responses.
 *
 *   3. SECURITY: We control exactly what information leaks in error
 *      responses. Stack traces, internal class names, and SQL errors
 *      are logged server-side but NEVER sent to the client.
 *
 *   4. LOGGING: Every error is logged with full context (exception class,
 *      message, request path) for post-mortem debugging.
 *
 * Handler Ordering:
 *   Handlers are matched from most-specific to least-specific. The
 *   generic Exception handler is the catch-all safety net.
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    // ========================================================================
    // VALIDATION ERRORS (400 Bad Request)
    // ========================================================================

    /**
     * Handles Jakarta Validation failures from @Valid on request bodies.
     *
     * When a DTO field fails validation (e.g., @NotBlank, @Email), Spring
     * throws MethodArgumentNotValidException containing ALL field errors.
     * We collect them into a single comma-separated message so the client
     * can display all validation issues at once (not one at a time).
     *
     * Example response:
     *   {
     *     "status": 400,
     *     "message": "email: Please provide a valid email address, amount: Amount must be greater than zero"
     *   }
     */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiErrorResponse> handleValidationException(
            MethodArgumentNotValidException ex,
            HttpServletRequest request) {

        // Collect all field-level validation errors into a readable string
        String message = ex.getBindingResult().getFieldErrors().stream()
                .map(this::formatFieldError)
                .collect(Collectors.joining("; "));

        log.warn("Validation failed for request to {}: {}", request.getRequestURI(), message);

        return buildErrorResponse(HttpStatus.BAD_REQUEST, message, request);
    }

    // ========================================================================
    // DOMAIN EXCEPTIONS
    // ========================================================================

    /**
     * Resource not found → 404.
     * Covers: Transaction not found, User not found, etc.
     */
    @ExceptionHandler(ResourceNotFoundException.class)
    public ResponseEntity<ApiErrorResponse> handleResourceNotFound(
            ResourceNotFoundException ex,
            HttpServletRequest request) {

        log.warn("Resource not found: {}", ex.getMessage());
        return buildErrorResponse(HttpStatus.NOT_FOUND, ex.getMessage(), request);
    }

    /**
     * Duplicate resource → 409 Conflict.
     * Covers: Email already registered, duplicate transaction, etc.
     */
    @ExceptionHandler(DuplicateResourceException.class)
    public ResponseEntity<ApiErrorResponse> handleDuplicateResource(
            DuplicateResourceException ex,
            HttpServletRequest request) {

        log.warn("Duplicate resource: {}", ex.getMessage());
        return buildErrorResponse(HttpStatus.CONFLICT, ex.getMessage(), request);
    }

    /**
     * Rate limit exceeded → 429 Too Many Requests.
     * Triggered by Bucket4j when dashboard endpoints are hit too frequently.
     */
    @ExceptionHandler(RateLimitExceededException.class)
    public ResponseEntity<ApiErrorResponse> handleRateLimitExceeded(
            RateLimitExceededException ex,
            HttpServletRequest request) {

        log.warn("Rate limit exceeded for IP {}: {}", request.getRemoteAddr(), ex.getMessage());
        return buildErrorResponse(HttpStatus.TOO_MANY_REQUESTS, ex.getMessage(), request);
    }

    // ========================================================================
    // SECURITY EXCEPTIONS
    // ========================================================================

    /**
     * Bad credentials → 401 Unauthorized.
     * Thrown when login fails due to wrong email/password.
     *
     * Security Note: We use a generic message instead of specifying
     * whether the email or password was wrong. This prevents user
     * enumeration attacks.
     */
    @ExceptionHandler(BadCredentialsException.class)
    public ResponseEntity<ApiErrorResponse> handleBadCredentials(
            BadCredentialsException ex,
            HttpServletRequest request) {

        log.warn("Authentication failed for request to {}", request.getRequestURI());
        return buildErrorResponse(
            HttpStatus.UNAUTHORIZED,
            "Invalid email or password",
            request
        );
    }

    /**
     * Generic authentication failure → 401.
     * Catches any AuthenticationException not handled by more specific handlers.
     */
    @ExceptionHandler(AuthenticationException.class)
    public ResponseEntity<ApiErrorResponse> handleAuthenticationException(
            AuthenticationException ex,
            HttpServletRequest request) {

        log.warn("Authentication error: {}", ex.getMessage());
        return buildErrorResponse(HttpStatus.UNAUTHORIZED, "Authentication failed", request);
    }

    /**
     * Access denied → 403 Forbidden.
     * Thrown when a user is authenticated but lacks the required role
     * (e.g., VIEWER trying to delete a transaction).
     */
    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ApiErrorResponse> handleAccessDenied(
            AccessDeniedException ex,
            HttpServletRequest request) {

        log.warn("Access denied for request to {}: {}", request.getRequestURI(), ex.getMessage());
        return buildErrorResponse(
            HttpStatus.FORBIDDEN,
            "You do not have permission to perform this action",
            request
        );
    }

    // ========================================================================
    // GENERIC / ILLEGAL ARGUMENT
    // ========================================================================

    /**
     * Illegal argument → 400.
     * Catches programmatic validation (service-layer throws) that isn't
     * covered by Jakarta Validation annotations.
     */
    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ApiErrorResponse> handleIllegalArgument(
            IllegalArgumentException ex,
            HttpServletRequest request) {

        log.warn("Illegal argument: {}", ex.getMessage());
        return buildErrorResponse(HttpStatus.BAD_REQUEST, ex.getMessage(), request);
    }

    // ========================================================================
    // CATCH-ALL SAFETY NET (500 Internal Server Error)
    // ========================================================================

    /**
     * Unhandled exception → 500.
     *
     * This is the safety net. If any exception slips past the specific
     * handlers above, this ensures the client STILL gets our standardised
     * error format (not Spring Boot's default Whitelabel error page or
     * a raw stack trace).
     *
     * CRITICAL: We log the full stack trace server-side for debugging,
     * but return a generic message to the client. Leaking internal
     * details (class names, SQL errors, stack traces) in API responses
     * is a security vulnerability (CWE-209).
     */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiErrorResponse> handleGenericException(
            Exception ex,
            HttpServletRequest request) {

        // Full stack trace in server logs for debugging
        log.error("Unhandled exception for request to {}: ", request.getRequestURI(), ex);

        return buildErrorResponse(
            HttpStatus.INTERNAL_SERVER_ERROR,
            "An unexpected error occurred. Please try again later.",
            request
        );
    }

    // ========================================================================
    // HELPER METHODS
    // ========================================================================

    /**
     * Factory method for consistent ApiErrorResponse construction.
     * Every handler delegates here to ensure the response format is
     * identical across all error types.
     */
    private ResponseEntity<ApiErrorResponse> buildErrorResponse(
            HttpStatus status,
            String message,
            HttpServletRequest request) {

        ApiErrorResponse error = ApiErrorResponse.builder()
                .timestamp(Instant.now())
                .status(status.value())
                .error(status.getReasonPhrase())
                .message(message)
                .path(request.getRequestURI())
                .build();

        return new ResponseEntity<>(error, status);
    }

    /**
     * Format a single FieldError into a readable "fieldName: message" string.
     */
    private String formatFieldError(FieldError fieldError) {
        return String.format("%s: %s", fieldError.getField(), fieldError.getDefaultMessage());
    }
}
