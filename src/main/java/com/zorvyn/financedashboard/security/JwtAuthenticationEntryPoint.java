package com.zorvyn.financedashboard.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.zorvyn.financedashboard.dto.response.ApiErrorResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.time.Instant;

/**
 * ============================================================================
 * JWT AUTHENTICATION ENTRY POINT — Unauthorised Request Handler
 * ============================================================================
 *
 * This class handles the case where a client tries to access a protected
 * endpoint without valid authentication (missing or invalid JWT).
 *
 * Without this class, Spring Security would return its default error page
 * (HTML) or a basic JSON response that doesn't match our standardised
 * ApiErrorResponse format.
 *
 * By implementing AuthenticationEntryPoint, we ensure that EVERY 401
 * Unauthorized response uses our consistent JSON error format, making
 * life easier for frontend developers.
 */
@Component
public class JwtAuthenticationEntryPoint implements AuthenticationEntryPoint {

    private static final Logger log = LoggerFactory.getLogger(JwtAuthenticationEntryPoint.class);
    private final ObjectMapper objectMapper = new ObjectMapper();

    /**
     * Called when an unauthenticated client tries to access a secured resource.
     *
     * We write the response directly (not via @ResponseBody) because this
     * is a servlet-level filter, not a controller method. The ObjectMapper
     * serialises our ApiErrorResponse to JSON manually.
     */
    @Override
    public void commence(HttpServletRequest request,
                         HttpServletResponse response,
                         AuthenticationException authException) throws IOException {

        log.warn("Unauthorised access attempt to {}: {}", request.getRequestURI(), authException.getMessage());

        ApiErrorResponse errorResponse = ApiErrorResponse.builder()
                .timestamp(Instant.now())
                .status(HttpStatus.UNAUTHORIZED.value())
                .error(HttpStatus.UNAUTHORIZED.getReasonPhrase())
                .message("Authentication required. Please provide a valid JWT token in the Authorization header.")
                .path(request.getRequestURI())
                .build();

        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);

        // Jackson's findAndRegisterModules() handles Java 8 time types
        objectMapper.findAndRegisterModules();
        objectMapper.writeValue(response.getOutputStream(), errorResponse);
    }
}
