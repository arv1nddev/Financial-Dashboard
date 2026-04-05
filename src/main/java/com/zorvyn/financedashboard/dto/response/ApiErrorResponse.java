package com.zorvyn.financedashboard.dto.response;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

/**
 * ============================================================================
 * STANDARDISED API ERROR RESPONSE
 * ============================================================================
 *
 * Every error from this API returns this exact structure. Consistency in
 * error responses is critical for:
 *
 *   1. FRONTEND INTEGRATION: The client team can build a single error
 *      handler that works for all endpoints.
 *   2. OBSERVABILITY: Log aggregation tools can parse a consistent schema.
 *   3. DEBUGGING: The path and timestamp make it trivial to correlate
 *      errors with server-side logs.
 *
 * This DTO is used exclusively by our @RestControllerAdvice
 * GlobalExceptionHandler. Individual controllers never construct this
 * directly — errors are thrown as exceptions and caught centrally.
 *
 * JSON Example:
 *   {
 *     "timestamp": "2026-04-05T12:00:00Z",
 *     "status": 404,
 *     "error": "Not Found",
 *     "message": "Transaction with ID 42 not found",
 *     "path": "/api/transactions/42"
 *   }
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ApiErrorResponse {

    /**
     * ISO 8601 timestamp of when the error occurred.
     * Using Instant (UTC) for timezone-agnostic error correlation.
     */
    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd'T'HH:mm:ss'Z'", timezone = "UTC")
    @Builder.Default
    private Instant timestamp = Instant.now();

    /**
     * HTTP status code (e.g., 400, 401, 404, 500).
     * Duplicated from the HTTP response for convenience when the
     * response body is logged separately from headers.
     */
    private int status;

    /**
     * HTTP status reason phrase (e.g., "Bad Request", "Not Found").
     * Provides a human-readable error category.
     */
    private String error;

    /**
     * Detailed, user-friendly error message.
     * For validation errors, this contains the specific field violations.
     * For system errors, this contains a safe message (never stack traces
     * or internal details that could aid attackers).
     */
    private String message;

    /**
     * The request URI that triggered the error.
     * Combined with timestamp, this enables precise log correlation.
     */
    private String path;
}
