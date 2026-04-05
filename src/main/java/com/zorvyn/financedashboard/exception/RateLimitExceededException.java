package com.zorvyn.financedashboard.exception;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

/**
 * ============================================================================
 * RATE LIMIT EXCEEDED EXCEPTION
 * ============================================================================
 *
 * Thrown by the RateLimiterInterceptor when a client exceeds the
 * configured request rate for protected endpoints (dashboard summaries).
 *
 * Maps to HTTP 429 Too Many Requests per RFC 6585.
 */
@ResponseStatus(HttpStatus.TOO_MANY_REQUESTS)
public class RateLimitExceededException extends RuntimeException {

    public RateLimitExceededException(String message) {
        super(message);
    }
}
