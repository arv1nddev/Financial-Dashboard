package com.zorvyn.financedashboard.dto.response;

import com.zorvyn.financedashboard.entity.Role;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * ============================================================================
 * AUTHENTICATION RESPONSE DTO
 * ============================================================================
 *
 * Returned after successful login or registration. Contains:
 *   - JWT access token for subsequent API calls
 *   - Token type (always "Bearer" per RFC 6750)
 *   - Basic user info to avoid an immediate follow-up GET /users/me call
 *
 * Security Notes:
 *   1. We include the role so the frontend can render role-appropriate UI
 *      without decoding the JWT. The server still validates the JWT's
 *      claims on every request — this is a UX optimisation, not a
 *      security delegation.
 *   2. The password is NEVER included in any response DTO.
 *   3. Token expiry is not included here; the client should decode the
 *      JWT's `exp` claim or use a fixed known duration.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AuthResponse {

    private String accessToken;

    @Builder.Default
    private String tokenType = "Bearer";

    private Long userId;
    private String fullName;
    private String email;
    private Role role;
}
