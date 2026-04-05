package com.zorvyn.financedashboard.config;

import com.zorvyn.financedashboard.exception.RateLimitExceededException;

import io.github.bucket4j.Bandwidth;
import io.github.bucket4j.Bucket;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * ============================================================================
 * RATE LIMITER INTERCEPTOR — Bucket4j Token-Bucket Rate Limiting
 * ============================================================================
 *
 * Implements per-client-IP rate limiting using the Token Bucket algorithm.
 *
 * Why Token Bucket (not Fixed Window or Sliding Window)?
 *
 *   1. BURST TOLERANCE: Token bucket allows short bursts (up to bucket
 *      capacity) while enforcing an average rate. Fixed window is too
 *      rigid; sliding window is more complex with minimal benefit.
 *
 *   2. SIMPLICITY: Bucket4j's API is clean and requires zero external
 *      infrastructure (unlike Redis-based rate limiting).
 *
 *   3. PREDICTABILITY: Clients get a clear, predictable rate allowance
 *      (20 requests per minute) with immediate feedback when exceeded.
 *
 * How Token Bucket Works:
 *   - Bucket starts with N tokens (capacity = 20)
 *   - Each request consumes 1 token
 *   - Tokens refill at a steady rate (20 per minute)
 *   - If no tokens available → request is rejected (429)
 *
 * Scoping:
 *   Rate limits are per-client-IP. In production with a reverse proxy,
 *   you'd use X-Forwarded-For header. For simplicity, we use
 *   request.getRemoteAddr() which works for direct connections.
 *
 * Why an Interceptor instead of a Filter?
 *   Interceptors run AFTER Spring Security's filter chain, so we only
 *   rate-limit authenticated requests. Filters run before security,
 *   which would rate-limit even unauthenticated requests (wasted effort).
 *   Also, interceptors can be selectively applied to specific URL
 *   patterns in WebMvcConfigurer.
 */
@Component
public class RateLimiterInterceptor implements HandlerInterceptor {

    private static final Logger log = LoggerFactory.getLogger(RateLimiterInterceptor.class);

    /**
     * ConcurrentHashMap stores one Bucket per client IP.
     *
     * Memory consideration: Each Bucket is lightweight (~200 bytes).
     * With 10,000 unique clients, that's ~2MB — negligible.
     * In production, you'd add periodic cleanup of stale entries
     * using a scheduled task or a cache with expiry (e.g., Caffeine).
     */
    private final Map<String, Bucket> bucketCache = new ConcurrentHashMap<>();

    private final long capacity;
    private final long refillTokens;
    private final long refillDurationSeconds;

    public RateLimiterInterceptor(
            @Value("${app.rate-limiter.capacity}") long capacity,
            @Value("${app.rate-limiter.refill-tokens}") long refillTokens,
            @Value("${app.rate-limiter.refill-duration-seconds}") long refillDurationSeconds) {
        this.capacity = capacity;
        this.refillTokens = refillTokens;
        this.refillDurationSeconds = refillDurationSeconds;
    }

    /**
     * Pre-handle: Called before the controller method executes.
     *
     * Returns true to continue processing, false to abort.
     * We throw RateLimitExceededException instead of returning false
     * so our GlobalExceptionHandler can produce a standardised error response.
     */
    @Override
    public boolean preHandle(
            @NonNull HttpServletRequest request,
            @NonNull HttpServletResponse response,
            @NonNull Object handler) {

        String clientIp = getClientIp(request);
        Bucket bucket = bucketCache.computeIfAbsent(clientIp, this::createNewBucket);

        /*
         * tryConsume(1) atomically checks if a token is available and
         * consumes it if so. This is thread-safe — Bucket4j handles
         * concurrent access from multiple servlet threads.
         */
        if (bucket.tryConsume(1)) {
            // Add rate-limit headers for client transparency (RFC draft standard)
            long availableTokens = bucket.getAvailableTokens();
            response.setHeader("X-Rate-Limit-Remaining", String.valueOf(availableTokens));
            response.setHeader("X-Rate-Limit-Limit", String.valueOf(capacity));
            return true;
        }

        log.warn("Rate limit exceeded for IP: {} on endpoint: {}", clientIp, request.getRequestURI());

        throw new RateLimitExceededException(
            "Rate limit exceeded. Maximum " + capacity + " requests per " +
            refillDurationSeconds + " seconds. Please try again later."
        );
    }

    /**
     * Create a new token bucket for a client IP.
     *
     * Bandwidth.builder() creates a rate limit specification:
     *   - capacity(20): Maximum burst size (bucket holds 20 tokens)
     *   - refillGreedy(20, Duration.ofSeconds(60)): Refill 20 tokens
     *     every 60 seconds, distributed linearly (1 token every 3 seconds)
     *
     * "Greedy" refill means tokens are added as fast as possible
     * (linearly). "Intervally" would add them all at once at the
     * interval boundary, causing bursty behaviour.
     */
    private Bucket createNewBucket(String clientIp) {
        Bandwidth bandwidth = Bandwidth.builder()
                .capacity(capacity)
                .refillGreedy(refillTokens, Duration.ofSeconds(refillDurationSeconds))
                .build();

        return Bucket.builder()
                .addLimit(bandwidth)
                .build();
    }

    /**
     * Extract client IP, preferring X-Forwarded-For for reverse proxy setups.
     *
     * Security Note: X-Forwarded-For can be spoofed by clients. In production,
     * configure your reverse proxy (Nginx/ALB) to overwrite this header
     * with the true client IP, and trust only the LAST value in the chain.
     */
    private String getClientIp(HttpServletRequest request) {
        String xForwardedFor = request.getHeader("X-Forwarded-For");
        if (xForwardedFor != null && !xForwardedFor.isEmpty()) {
            // Take the first IP in the chain (client's original IP)
            return xForwardedFor.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }
}
