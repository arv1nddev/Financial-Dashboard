package com.zorvyn.financedashboard.dto.request;

import com.zorvyn.financedashboard.entity.Role;
import jakarta.validation.constraints.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * ============================================================================
 * REGISTER REQUEST DTO
 * ============================================================================
 *
 * Inbound DTO for user registration. Each field has layered validation:
 *   - @NotBlank for presence (rejects null, empty, and whitespace)
 *   - @Size for length bounds
 *   - @Email for format (RFC 5322 subset)
 *   - @Pattern for complexity rules (password)
 *
 * Design Decision — Role in Registration:
 *   We include role in the registration payload to support admin-initiated
 *   user creation (POST /auth/register is protected to ADMIN only in a
 *   production deployment). For self-registration, the controller defaults
 *   to VIEWER and ignores this field.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RegisterRequest {

    @NotBlank(message = "Full name is required")
    @Size(min = 2, max = 150, message = "Full name must be between 2 and 150 characters")
    private String fullName;

    @NotBlank(message = "Email is required")
    @Email(message = "Please provide a valid email address")
    @Size(max = 255, message = "Email must not exceed 255 characters")
    private String email;

    /**
     * Password complexity rules enforced via regex:
     *   - At least 8 characters
     *   - At least one uppercase letter
     *   - At least one lowercase letter
     *   - At least one digit
     *   - At least one special character
     *
     * These rules align with NIST SP 800-63B guidelines (2024 revision)
     * which recommends minimum 8 characters with complexity requirements.
     */
    @NotBlank(message = "Password is required")
    @Size(min = 8, max = 72, message = "Password must be between 8 and 72 characters")
    @Pattern(
        regexp = "^(?=.*[a-z])(?=.*[A-Z])(?=.*\\d)(?=.*[@$!%*?&#])[A-Za-z\\d@$!%*?&#]{8,}$",
        message = "Password must contain at least one uppercase letter, one lowercase letter, one digit, and one special character"
    )
    private String password;

    /**
     * Optional role assignment. If null, the service layer defaults to VIEWER.
     * Only ADMIN users can assign ADMIN or ANALYST roles.
     */
    private Role role;
}
