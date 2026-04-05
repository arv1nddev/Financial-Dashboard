package com.zorvyn.financedashboard.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.lang.NonNull;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * ============================================================================
 * JWT AUTHENTICATION FILTER — Stateless Token Validation on Every Request
 * ============================================================================
 *
 * This filter runs ONCE per HTTP request (guaranteed by OncePerRequestFilter)
 * and performs the following:
 *
 *   1. Extract the JWT from the Authorization header (Bearer scheme)
 *   2. Validate the token (signature, expiry, structure)
 *   3. Load the user from the database
 *   4. Set the authentication in Spring Security's SecurityContext
 *
 * Why OncePerRequestFilter?
 *   In servlet architectures with forwards/includes, a filter can execute
 *   multiple times for a single client request. OncePerRequestFilter
 *   guarantees exactly-once execution, preventing duplicate auth processing.
 *
 * Stateless Session Strategy:
 *   This filter is the ONLY way users get authenticated. There are no
 *   sessions, no cookies, no CSRF tokens. The JWT IS the session.
 *   This means:
 *   1. No server-side session storage (horizontally scalable)
 *   2. No JSESSIONID cookie (eliminates session fixation attacks)
 *   3. No CSRF protection needed (CSRF exploits cookie-based auth)
 *   4. Every request is self-contained (REST principle)
 *
 * Filter Ordering:
 *   This filter is registered BEFORE UsernamePasswordAuthenticationFilter
 *   in the Spring Security filter chain (see SecurityConfig). This ensures
 *   JWT-based auth is attempted before Spring Security's default form-based
 *   authentication.
 */
@Component
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(JwtAuthenticationFilter.class);

    /*
     * We expect the Authorization header to look like:
     *   Authorization: Bearer eyJhbGciOiJIUzUxMiJ9.eyJ...
     *
     * The "Bearer " prefix is 7 characters long. We strip it to get
     * the raw token. This follows RFC 6750 (Bearer Token Usage).
     */
    private static final String BEARER_PREFIX = "Bearer ";

    private final JwtTokenProvider jwtTokenProvider;
    private final CustomUserDetailsService userDetailsService;

    public JwtAuthenticationFilter(JwtTokenProvider jwtTokenProvider,
                                    CustomUserDetailsService userDetailsService) {
        this.jwtTokenProvider = jwtTokenProvider;
        this.userDetailsService = userDetailsService;
    }

    /**
     * Core filter logic executed on every incoming HTTP request.
     *
     * Flow:
     *   1. Try to extract JWT from Authorization header
     *   2. If present and valid → authenticate the user
     *   3. If absent or invalid → continue the filter chain unauthenticated
     *      (the request may be to a public endpoint like /auth/login)
     *
     * Note: We check SecurityContextHolder.getContext().getAuthentication() == null
     * to avoid re-authenticating if another filter already set the context.
     * This is a defensive check — in practice, only this filter sets it.
     */
    @Override
    protected void doFilterInternal(
            @NonNull HttpServletRequest request,
            @NonNull HttpServletResponse response,
            @NonNull FilterChain filterChain) throws ServletException, IOException {

        try {
            String jwt = extractJwtFromRequest(request);

            if (StringUtils.hasText(jwt) && jwtTokenProvider.validateToken(jwt)) {
                /*
                 * Token is valid — load the user from the database.
                 *
                 * Why load from DB on every request instead of trusting the JWT claims?
                 *   1. The user might have been deactivated since the JWT was issued.
                 *   2. The user's role might have changed.
                 *   3. The user might have been deleted.
                 *
                 * This is a conscious trade-off: one DB query per request vs.
                 * the risk of stale JWT claims. In a high-throughput system,
                 * you could cache UserDetails in Redis with a short TTL.
                 */
                String email = jwtTokenProvider.getEmailFromToken(jwt);
                UserDetails userDetails = userDetailsService.loadUserByUsername(email);

                /*
                 * Create an authentication token with the loaded user details.
                 *
                 * UsernamePasswordAuthenticationToken(principal, credentials, authorities):
                 *   - principal: the UserDetails (contains user info)
                 *   - credentials: null (we don't need the password post-authentication)
                 *   - authorities: user's granted authorities (roles)
                 *
                 * The 3-arg constructor marks the token as "authenticated".
                 * The 2-arg constructor would NOT mark it as authenticated.
                 */
                UsernamePasswordAuthenticationToken authentication =
                    new UsernamePasswordAuthenticationToken(
                        userDetails,
                        null,
                        userDetails.getAuthorities()
                    );

                /*
                 * Attach request details (IP address, session ID) to the
                 * authentication for audit logging purposes.
                 */
                authentication.setDetails(
                    new WebAuthenticationDetailsSource().buildDetails(request)
                );

                /*
                 * Set the authentication in the SecurityContext.
                 * All downstream @PreAuthorize checks, Principal injections,
                 * and SecurityContextHolder.getContext().getAuthentication()
                 * calls will now see this authenticated user.
                 */
                SecurityContextHolder.getContext().setAuthentication(authentication);

                log.debug("Authenticated user '{}' for request: {} {}",
                    email, request.getMethod(), request.getRequestURI());
            }
        } catch (Exception ex) {
            /*
             * We catch ALL exceptions here to prevent authentication failures
             * from crashing the entire request pipeline. If JWT validation
             * fails for any reason, the request continues UNAUTHENTICATED.
             * Protected endpoints will return 401/403 as appropriate.
             */
            log.error("Cannot set user authentication: {}", ex.getMessage());
        }

        // Always continue the filter chain — even if authentication failed.
        // Unauthenticated requests will be handled by Spring Security's
        // access decision manager (returning 401 or 403 as appropriate).
        filterChain.doFilter(request, response);
    }

    /**
     * Extract the JWT token from the Authorization header.
     *
     * @param request the incoming HTTP request
     * @return the raw JWT string, or null if not present
     */
    private String extractJwtFromRequest(HttpServletRequest request) {
        String bearerToken = request.getHeader("Authorization");
        if (StringUtils.hasText(bearerToken) && bearerToken.startsWith(BEARER_PREFIX)) {
            return bearerToken.substring(BEARER_PREFIX.length());
        }
        return null;
    }
}
