package com.zorvyn.financedashboard.dto.request;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * ============================================================================
 * LOGIN REQUEST DTO
 * ============================================================================
 *
 * Inbound DTO for the /auth/login endpoint. We use Jakarta Validation
 * annotations to reject malformed requests at the controller boundary,
 * BEFORE they reach the service layer. This is the "fail fast" pattern:
 *
 *   1. Validation errors return 400 Bad Request immediately.
 *   2. The service layer can assume valid input (no defensive null checks).
 *   3. Error messages are user-friendly and standardised via our
 *      GlobalExceptionHandler.
 *
 * Security Note:
 *   We intentionally do NOT include the password in toString() or any
 *   logging output. Lombok's @Data generates toString(), but the password
 *   field should never appear in logs. In a production system, we'd use
 *   @ToString.Exclude on the password field.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class LoginRequest {

    /**
     * Standard email validation ensures syntactic correctness.
     * The @NotBlank check rejects empty strings and whitespace-only input,
     * which @Email alone would not catch.
     */
    @NotBlank(message = "Email is required")
    @Email(message = "Please provide a valid email address")
    private String email;

    /**
     * Password minimum length enforced at the DTO level.
     * BCrypt has a maximum input length of 72 bytes — we don't enforce
     * a max here because BCrypt truncates silently, but we document it.
     */
    @NotBlank(message = "Password is required")
    @Size(min = 6, message = "Password must be at least 6 characters")
    private String password;
}
