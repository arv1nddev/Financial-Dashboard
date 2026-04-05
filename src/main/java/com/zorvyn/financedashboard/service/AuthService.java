package com.zorvyn.financedashboard.service;

import com.zorvyn.financedashboard.dto.request.LoginRequest;
import com.zorvyn.financedashboard.dto.request.RegisterRequest;
import com.zorvyn.financedashboard.dto.response.AuthResponse;
import com.zorvyn.financedashboard.entity.Role;
import com.zorvyn.financedashboard.entity.User;
import com.zorvyn.financedashboard.exception.DuplicateResourceException;
import com.zorvyn.financedashboard.repository.UserRepository;
import com.zorvyn.financedashboard.security.CustomUserDetails;
import com.zorvyn.financedashboard.security.JwtTokenProvider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * ============================================================================
 * AUTH SERVICE — Authentication & User Registration Business Logic
 * ============================================================================
 *
 * Encapsulates the authentication flow and user creation.
 *
 * Separation of Concerns:
 *   - Controller: HTTP request/response handling, validation trigger
 *   - Service (this): Business logic, security orchestration
 *   - Repository: Data access only
 *
 * This service does NOT extend or implement any interface. In Clean
 * Architecture, you'd define an AuthUseCase interface in the domain layer
 * and implement it here. For this project's scope, the concrete class
 * is sufficient — introducing an interface for a single implementation
 * would be over-engineering.
 *
 * Transaction Management:
 *   Registration is @Transactional because it involves a write operation.
 *   Login is read-only (no data modification).
 */
@Service
public class AuthService {

    private static final Logger log = LoggerFactory.getLogger(AuthService.class);

    private final AuthenticationManager authenticationManager;
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtTokenProvider jwtTokenProvider;

    public AuthService(AuthenticationManager authenticationManager,
                       UserRepository userRepository,
                       PasswordEncoder passwordEncoder,
                       JwtTokenProvider jwtTokenProvider) {
        this.authenticationManager = authenticationManager;
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtTokenProvider = jwtTokenProvider;
    }

    /**
     * Authenticate a user and generate a JWT token.
     *
     * Flow:
     *   1. AuthenticationManager delegates to DaoAuthenticationProvider
     *   2. DaoAuthenticationProvider calls UserDetailsService.loadUserByUsername()
     *   3. Loaded user's BCrypt password is compared with the submitted password
     *   4. If match → Authentication object is returned
     *   5. If mismatch → BadCredentialsException is thrown
     *   6. We generate a JWT from the authenticated principal
     *
     * Why we set the SecurityContext here:
     *   This sets the authentication for the CURRENT request only.
     *   Since our sessions are stateless, this is purely for any
     *   downstream @PreAuthorize checks within the same request.
     *
     * @param loginRequest validated login credentials
     * @return AuthResponse containing the JWT and user info
     * @throws org.springframework.security.authentication.BadCredentialsException
     *         if authentication fails
     */
    public AuthResponse login(LoginRequest loginRequest) {
        log.info("Login attempt for email: {}", loginRequest.getEmail());

        /*
         * This single line triggers the ENTIRE Spring Security
         * authentication pipeline:
         *   UsernamePasswordAuthenticationToken (unauthenticated)
         *     → AuthenticationManager
         *       → DaoAuthenticationProvider
         *         → UserDetailsService.loadUserByUsername()
         *         → PasswordEncoder.matches()
         *     → Authentication (authenticated) or BadCredentialsException
         */
        Authentication authentication = authenticationManager.authenticate(
            new UsernamePasswordAuthenticationToken(
                loginRequest.getEmail(),
                loginRequest.getPassword()
            )
        );

        SecurityContextHolder.getContext().setAuthentication(authentication);

        CustomUserDetails userDetails = (CustomUserDetails) authentication.getPrincipal();
        String token = jwtTokenProvider.generateToken(authentication, userDetails.getId());

        log.info("User '{}' authenticated successfully", loginRequest.getEmail());

        return AuthResponse.builder()
                .accessToken(token)
                .tokenType("Bearer")
                .userId(userDetails.getId())
                .fullName(userDetails.getFullName())
                .email(userDetails.getEmail())
                .role(Role.valueOf(
                    userDetails.getAuthorities().iterator().next()
                        .getAuthority().replace("ROLE_", "")
                ))
                .build();
    }

    /**
     * Register a new user account.
     *
     * Security checks:
     *   1. Duplicate email check (before attempting insert)
     *   2. Password is BCrypt-hashed before storage
     *   3. Default role is VIEWER (least privilege principle)
     *
     * Why we check existsByEmail() before saving:
     *   The database unique constraint would catch duplicates too, but:
     *   - existsByEmail() gives a clear, user-friendly error message
     *   - The DB constraint throws DataIntegrityViolationException with
     *     a cryptic Postgres error message
     *   - Both checks together form a defence-in-depth strategy
     *
     * @param registerRequest validated registration data
     * @return AuthResponse with JWT for immediate authenticated access
     */
    @Transactional
    public AuthResponse register(RegisterRequest registerRequest) {
        log.info("Registration attempt for email: {}", registerRequest.getEmail());

        // ── Duplicate Check ──────────────────────────────────────────
        // Check BEFORE insert for a user-friendly error message.
        // The DB unique constraint is a backup safety net.
        if (userRepository.existsByEmail(registerRequest.getEmail())) {
            throw new DuplicateResourceException("User", "email", registerRequest.getEmail());
        }

        // ── Create User Entity ───────────────────────────────────────
        // Password is hashed here, BEFORE it touches the database.
        // The plaintext password exists in memory only during this method call.
        User user = User.builder()
                .fullName(registerRequest.getFullName())
                .email(registerRequest.getEmail().toLowerCase().trim()) // Normalise email
                .password(passwordEncoder.encode(registerRequest.getPassword()))
                .role(registerRequest.getRole() != null ? registerRequest.getRole() : Role.VIEWER)
                .isActive(true)
                .build();

        User savedUser = userRepository.save(user);

        log.info("User registered successfully: {} with role {}", savedUser.getEmail(), savedUser.getRole());

        // ── Auto-login after registration ────────────────────────────
        // Generate JWT immediately so the user doesn't need a separate
        // login call. This is a common UX pattern in modern APIs.
        Authentication authentication = authenticationManager.authenticate(
            new UsernamePasswordAuthenticationToken(
                registerRequest.getEmail(),
                registerRequest.getPassword()
            )
        );

        SecurityContextHolder.getContext().setAuthentication(authentication);

        CustomUserDetails userDetails = (CustomUserDetails) authentication.getPrincipal();
        String token = jwtTokenProvider.generateToken(authentication, savedUser.getId());

        return AuthResponse.builder()
                .accessToken(token)
                .tokenType("Bearer")
                .userId(savedUser.getId())
                .fullName(savedUser.getFullName())
                .email(savedUser.getEmail())
                .role(savedUser.getRole())
                .build();
    }
}
