package com.zorvyn.financedashboard.config;

import com.zorvyn.financedashboard.security.JwtAuthenticationEntryPoint;
import com.zorvyn.financedashboard.security.JwtAuthenticationFilter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

/**
 * ============================================================================
 * SECURITY CONFIGURATION — The Fortress Gate
 * ============================================================================
 *
 * This is the most critical configuration class in the application. It
 * defines WHO can access WHAT and HOW authentication works.
 *
 * Architecture: Stateless JWT + Method-Level RBAC
 * ─────────────────────────────────────────────────
 *
 * Request Flow:
 *   Client → JwtAuthenticationFilter → SecurityFilterChain → Controller
 *                    │                         │
 *                    │ Extract & validate JWT   │ Check @PreAuthorize
 *                    │ Set SecurityContext       │ role requirements
 *                    ▼                         ▼
 *              Valid token?              Correct role?
 *              ├─ Yes → Continue         ├─ Yes → Process request
 *              └─ No  → 401             └─ No  → 403
 *
 * Security Layers:
 *   1. URL-level security (this class) → coarse-grained path protection
 *   2. Method-level security (@PreAuthorize) → fine-grained RBAC on
 *      individual controller methods
 *
 * @EnableMethodSecurity activates @PreAuthorize / @PostAuthorize annotations
 * on controller methods. Without this, all @PreAuthorize annotations would
 * be silently ignored — a dangerous silent failure.
 */
@Configuration
@EnableWebSecurity
@EnableMethodSecurity  // Activates @PreAuthorize on controller methods
public class SecurityConfig {

    private final JwtAuthenticationFilter jwtAuthenticationFilter;
    private final JwtAuthenticationEntryPoint jwtAuthenticationEntryPoint;
    private final UserDetailsService userDetailsService;

    public SecurityConfig(JwtAuthenticationFilter jwtAuthenticationFilter,
                          JwtAuthenticationEntryPoint jwtAuthenticationEntryPoint,
                          UserDetailsService userDetailsService) {
        this.jwtAuthenticationFilter = jwtAuthenticationFilter;
        this.jwtAuthenticationEntryPoint = jwtAuthenticationEntryPoint;
        this.userDetailsService = userDetailsService;
    }

    /**
     * BCrypt password encoder bean.
     *
     * BCrypt was chosen over alternatives for these reasons:
     *   - PBKDF2: Less resistant to GPU-based attacks
     *   - SCrypt: Better GPU resistance but higher memory requirements
     *   - Argon2: Superior but not yet widely adopted; BCrypt is battle-tested
     *   - SHA-256/512: NOT a password hash — it's a fast hash, which is
     *     exactly what you DON'T want for passwords
     *
     * The default BCrypt strength is 10 (2^10 = 1024 iterations).
     * Each additional strength value doubles the computation time.
     * We use the default because:
     *   - It takes ~100ms per hash on modern hardware
     *   - Fast enough for login (user won't notice)
     *   - Slow enough to make brute-force impractical
     *     (~8 million years for a 8-char alphanumeric password)
     */
    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    /**
     * Authentication provider that uses our CustomUserDetailsService
     * and BCrypt password encoder. This is what Spring Security uses
     * to verify credentials during login.
     *
     * The DaoAuthenticationProvider flow:
     *   1. Load user by email via UserDetailsService
     *   2. Compare submitted password with stored BCrypt hash
     *   3. Check account status (active, not locked, etc.)
     *   4. Return authenticated token or throw AuthenticationException
     */
    @Bean
    public DaoAuthenticationProvider authenticationProvider() {
        DaoAuthenticationProvider provider = new DaoAuthenticationProvider();
        provider.setUserDetailsService(userDetailsService);
        provider.setPasswordEncoder(passwordEncoder());
        return provider;
    }

    /**
     * AuthenticationManager bean — needed by our AuthService to
     * programmatically trigger authentication during login.
     */
    @Bean
    public AuthenticationManager authenticationManager(
            AuthenticationConfiguration config) throws Exception {
        return config.getAuthenticationManager();
    }

    /**
     * The Security Filter Chain — defines URL access rules and filter ordering.
     *
     * This is the single most important security configuration in the app.
     * Every decision here is deliberate and documented.
     */
    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
            // ================================================================
            // CSRF PROTECTION: DISABLED
            // ================================================================
            // CSRF attacks exploit cookie-based authentication by tricking
            // the browser into sending cookies with cross-origin requests.
            // Since we use JWT tokens in the Authorization HEADER (not cookies),
            // CSRF is not applicable. A malicious site cannot set headers
            // on cross-origin requests via <form> or <img> tags.
            //
            // If we ever add cookie-based authentication, CSRF must be
            // re-enabled immediately.
            // ================================================================
            .csrf(AbstractHttpConfigurer::disable)

            // ================================================================
            // SESSION MANAGEMENT: STATELESS
            // ================================================================
            // No HTTP sessions are created or used. Each request must carry
            // its own JWT. This gives us:
            //   1. Horizontal scalability (no sticky sessions needed)
            //   2. No session fixation vulnerability
            //   3. No server-side session storage overhead
            //   4. True RESTful statelessness
            // ================================================================
            .sessionManagement(session ->
                session.sessionCreationPolicy(SessionCreationPolicy.STATELESS)
            )

            // ================================================================
            // AUTHENTICATION ENTRY POINT
            // ================================================================
            // When an unauthenticated request hits a protected endpoint,
            // return our standardised JSON error response (not Spring's
            // default HTML error page).
            // ================================================================
            .exceptionHandling(exception ->
                exception.authenticationEntryPoint(jwtAuthenticationEntryPoint)
            )

            // ================================================================
            // URL-LEVEL ACCESS RULES
            // ================================================================
            // These are evaluated in ORDER. The first matching rule wins.
            // Put more specific rules before more general ones.
            //
            // Strategy:
            //   - PUBLIC: Auth endpoints (login/register) + Swagger docs
            //   - AUTHENTICATED: Everything else requires a valid JWT
            //   - ROLE-BASED: Fine-grained control via @PreAuthorize on
            //     individual controller methods (not here)
            // ================================================================
            .authorizeHttpRequests(auth -> auth
                // Auth endpoints must be public (chicken-and-egg: can't
                // require a JWT to GET a JWT)
                .requestMatchers("/auth/**").permitAll()

                // Swagger/OpenAPI docs — public for developer experience
                // In production, you might restrict these to internal networks
                .requestMatchers(
                    "/swagger-ui/**",
                    "/swagger-ui.html",
                    "/v3/api-docs/**",
                    "/v3/api-docs"
                ).permitAll()

                // Health check endpoint (if actuator is added later)
                .requestMatchers("/actuator/health").permitAll()

                // All other endpoints require authentication
                // Role-based checks are done via @PreAuthorize on methods
                .anyRequest().authenticated()
            )

            // ================================================================
            // AUTHENTICATION PROVIDER
            // ================================================================
            .authenticationProvider(authenticationProvider())

            // ================================================================
            // JWT FILTER PLACEMENT
            // ================================================================
            // Insert our JWT filter BEFORE Spring's UsernamePasswordAuthFilter.
            // This ensures JWT-based auth is attempted first. If the JWT is
            // valid, the SecurityContext is populated before any downstream
            // filters execute.
            // ================================================================
            .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }
}
