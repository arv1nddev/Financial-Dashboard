package com.zorvyn.financedashboard.security;

import io.jsonwebtoken.*;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import io.jsonwebtoken.security.SignatureException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.util.Date;
import java.util.stream.Collectors;

/**
 * ============================================================================
 * JWT TOKEN PROVIDER — Token Generation & Validation Service
 * ============================================================================
 *
 * Centralises all JWT operations: creation, parsing, and validation.
 *
 * Algorithm Choice — HMAC-SHA512 (HS512):
 *   We use a symmetric signing algorithm because this is a monolithic
 *   application where the same service issues AND validates tokens.
 *   HS512 provides 256-bit security (equivalent to AES-256) and is
 *   computationally efficient.
 *
 *   When to switch to RSA/ECDSA asymmetric signing:
 *   - Microservices architecture where separate services validate tokens
 *   - Third-party token consumers who shouldn't have the signing key
 *   - OpenID Connect compliance requirements
 *
 * Token Claims Structure:
 *   {
 *     "sub": "user@example.com",        → Subject (user identifier)
 *     "userId": 42,                      → User's database ID
 *     "roles": "ROLE_ADMIN",             → Comma-separated authorities
 *     "iss": "finance-dashboard-api",    → Issuer (prevents cross-app usage)
 *     "iat": 1712345678,                → Issued-at timestamp
 *     "exp": 1712432078                 → Expiration timestamp
 *   }
 *
 * Security Considerations:
 *   1. The secret key is injected from configuration (not hardcoded).
 *   2. We validate expiry, signature, and structure on every request.
 *   3. Failed validations are logged with the failure reason but return
 *      a generic "invalid token" to the client (prevents oracle attacks).
 */
@Component
public class JwtTokenProvider {

    private static final Logger log = LoggerFactory.getLogger(JwtTokenProvider.class);

    private final SecretKey signingKey;
    private final long expirationMs;
    private final String issuer;

    /**
     * Constructor injection with @Value for externalised configuration.
     *
     * The secret is Base64-encoded in application.yml and decoded here
     * into a proper SecretKey. This ensures the key meets HMAC-SHA512's
     * minimum length requirement (512 bits = 64 bytes).
     */
    public JwtTokenProvider(
            @Value("${app.jwt.secret}") String secretBase64,
            @Value("${app.jwt.expiration-ms}") long expirationMs,
            @Value("${app.jwt.issuer}") String issuer) {

        /*
         * Keys.hmacShaKeyFor() validates that the key material is at least
         * 512 bits for HS512. If the provided secret is too short, it throws
         * WeakKeyException at startup — fail fast, don't discover in prod.
         */
        this.signingKey = Keys.hmacShaKeyFor(Decoders.BASE64.decode(secretBase64));
        this.expirationMs = expirationMs;
        this.issuer = issuer;

        log.info("JWT Token Provider initialized — issuer: {}, expiration: {}ms", issuer, expirationMs);
    }

    /**
     * Generate a JWT token from an authenticated Spring Security principal.
     *
     * This method is called after successful authentication (login endpoint).
     * It encodes the user's identity and roles into the token claims.
     *
     * @param authentication the authenticated principal from Spring Security
     * @param userId the user's database ID (stored as a custom claim)
     * @return a signed JWT string
     */
    public String generateToken(Authentication authentication, Long userId) {
        CustomUserDetails userDetails = (CustomUserDetails) authentication.getPrincipal();

        Date now = new Date();
        Date expiryDate = new Date(now.getTime() + expirationMs);

        /*
         * We include roles as a comma-separated string claim rather than
         * a JSON array because:
         *   1. Simpler parsing on the validation side
         *   2. JJWT handles string claims natively
         *   3. We only have one role per user anyway
         */
        String roles = userDetails.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .collect(Collectors.joining(","));

        return Jwts.builder()
                .subject(userDetails.getUsername())       // Email as subject
                .claim("userId", userId)                  // DB ID for service-layer lookups
                .claim("roles", roles)                    // Authorities for @PreAuthorize
                .issuer(issuer)                           // Prevents cross-application token reuse
                .issuedAt(now)
                .expiration(expiryDate)
                .signWith(signingKey, Jwts.SIG.HS512)     // Sign with HS512
                .compact();
    }

    /**
     * Extract the subject (email) from a JWT token.
     *
     * Called by JwtAuthenticationFilter to identify the user on each request.
     * At this point, the token has already been validated by validateToken().
     *
     * @param token the JWT string
     * @return the email address from the token's subject claim
     */
    public String getEmailFromToken(String token) {
        return parseTokenClaims(token).getSubject();
    }

    /**
     * Extract the user's database ID from a JWT token.
     *
     * @param token the JWT string
     * @return the userId from the token's custom claim
     */
    public Long getUserIdFromToken(String token) {
        return parseTokenClaims(token).get("userId", Long.class);
    }

    /**
     * Validate a JWT token's integrity, expiry, and structure.
     *
     * This is the security gate — called on EVERY authenticated request.
     * All failure modes are caught and logged individually for debugging,
     * but the method returns a simple boolean to prevent information leakage.
     *
     * Validated checks:
     *   1. Signature matches (token wasn't tampered with)
     *   2. Token hasn't expired
     *   3. Token structure is valid JWT format
     *   4. Claims are well-formed
     *
     * @param token the JWT string to validate
     * @return true if the token is valid, false otherwise
     */
    public boolean validateToken(String token) {
        try {
            parseTokenClaims(token);
            return true;
        } catch (SignatureException ex) {
            // Someone tampered with the token or used a different signing key
            log.error("Invalid JWT signature: {}", ex.getMessage());
        } catch (MalformedJwtException ex) {
            // Not a valid JWT structure (missing parts, invalid encoding)
            log.error("Malformed JWT token: {}", ex.getMessage());
        } catch (ExpiredJwtException ex) {
            // Token's exp claim is in the past — user needs to re-authenticate
            log.error("Expired JWT token: {}", ex.getMessage());
        } catch (UnsupportedJwtException ex) {
            // JWT uses an algorithm or feature we don't support
            log.error("Unsupported JWT token: {}", ex.getMessage());
        } catch (IllegalArgumentException ex) {
            // Token string is null, empty, or whitespace
            log.error("JWT claims string is empty: {}", ex.getMessage());
        }
        return false;
    }

    /**
     * Parse and return the claims body from a JWT token.
     *
     * This is the internal workhorse method. It uses JJWT's parser builder
     * which validates the signature and checks expiry automatically.
     *
     * @param token the JWT string
     * @return parsed Claims object
     * @throws JwtException if the token is invalid
     */
    private Claims parseTokenClaims(String token) {
        return Jwts.parser()
                .verifyWith(signingKey)
                .requireIssuer(issuer)  // Reject tokens from other issuers
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }
}
