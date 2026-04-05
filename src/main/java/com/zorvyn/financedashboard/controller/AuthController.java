package com.zorvyn.financedashboard.controller;

import com.zorvyn.financedashboard.dto.request.LoginRequest;
import com.zorvyn.financedashboard.dto.request.RegisterRequest;
import com.zorvyn.financedashboard.dto.response.AuthResponse;
import com.zorvyn.financedashboard.service.AuthService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirements;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/**
 * ============================================================================
 * AUTH CONTROLLER — Authentication & Registration Endpoints
 * ============================================================================
 *
 * PUBLIC endpoints (no JWT required):
 *   POST /auth/login    → Authenticate and receive JWT
 *   POST /auth/register → Create account and receive JWT
 *
 * Controller Responsibilities (and nothing more):
 *   1. Accept and deserialise HTTP requests
 *   2. Trigger validation via @Valid
 *   3. Delegate to AuthService for business logic
 *   4. Return appropriate HTTP status codes
 *
 * The controller does NOT contain business logic — no password hashing,
 * no JWT generation, no database access. This is the "thin controller"
 * pattern from Clean Architecture.
 *
 * @SecurityRequirements({}) on each method tells Swagger UI these endpoints
 * don't require a Bearer token (overriding the global security requirement
 * defined in OpenApiConfig).
 */
@RestController
@RequestMapping("/auth")
@Tag(name = "Authentication", description = "Login and registration endpoints — no JWT required")
public class AuthController {

    private static final Logger log = LoggerFactory.getLogger(AuthController.class);

    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    /**
     * Authenticate a user with email and password.
     *
     * Success Response: 200 OK with JWT token and user info.
     * Failure Response: 401 Unauthorized (handled by GlobalExceptionHandler).
     *
     * @param loginRequest validated login credentials
     * @return JWT token and user details
     */
    @PostMapping("/login")
    @SecurityRequirements({})  // No auth required for login (obviously)
    @Operation(
        summary = "Login",
        description = "Authenticate with email and password. Returns a JWT token for subsequent API calls."
    )
    public ResponseEntity<AuthResponse> login(@Valid @RequestBody LoginRequest loginRequest) {
        log.info("POST /auth/login — email: {}", loginRequest.getEmail());

        AuthResponse response = authService.login(loginRequest);

        return ResponseEntity.ok(response);
    }

    /**
     * Register a new user account.
     *
     * Success Response: 201 Created with JWT token (auto-login).
     * Failure Response: 409 Conflict if email already exists.
     *
     * Returns 201 (not 200) because a new resource (user) is created.
     * The response includes a JWT so the client can immediately make
     * authenticated requests without a separate login call.
     *
     * @param registerRequest validated registration data
     * @return JWT token and new user details
     */
    @PostMapping("/register")
    @SecurityRequirements({})  // No auth required for registration
    @Operation(
        summary = "Register",
        description = "Create a new user account. Returns a JWT token for immediate authenticated access."
    )
    public ResponseEntity<AuthResponse> register(@Valid @RequestBody RegisterRequest registerRequest) {
        log.info("POST /auth/register — email: {}", registerRequest.getEmail());

        AuthResponse response = authService.register(registerRequest);

        /*
         * 201 Created is semantically correct for resource creation.
         * We don't include a Location header because the "resource"
         * is a user, not a REST resource with a canonical URL.
         */
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }
}
