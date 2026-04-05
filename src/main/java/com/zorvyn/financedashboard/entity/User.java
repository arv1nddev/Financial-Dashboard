package com.zorvyn.financedashboard.entity;

import jakarta.persistence.*;
import lombok.*;

import java.io.Serial;

/**
 * ============================================================================
 * USER ENTITY — Authentication & Authorization Principal
 * ============================================================================
 *
 * Maps to the `users` table. This entity is the core of our RBAC system.
 *
 * Data Modelling Decisions:
 *
 *   1. SINGLE ROLE PER USER (not many-to-many):
 *      Financial orgs typically assign one role per user. A user is either
 *      a viewer, an analyst, or an admin — not a combination. This
 *      simplifies permission checks to a single column lookup instead of
 *      a JOIN against a pivot table. If multi-role requirements emerge,
 *      we can refactor to a @ManyToMany with a roles bridge table.
 *
 *   2. PASSWORD STORAGE:
 *      Passwords are BCrypt-hashed BEFORE reaching this entity. The column
 *      length of 72 accommodates BCrypt's 60-character output with room
 *      for future algorithm upgrades (e.g., Argon2 produces longer hashes).
 *
 *   3. ACTIVE FLAG:
 *      Soft-disable for users (distinct from the transaction soft-delete).
 *      A deactivated user's JWT tokens should be rejected at the security
 *      filter level — see CustomUserDetailsService.
 *
 *   4. TABLE NAME 'users':
 *      We quote the table name because "user" is a reserved word in
 *      PostgreSQL. Using the plural form "users" avoids the issue, but
 *      we add @Table(name = "users") explicitly for clarity.
 *
 * Security Invariant:
 *   The password field is NEVER exposed in any DTO or API response.
 *   This is enforced at the DTO mapping layer, not here.
 */
@Entity
@Table(
    name = "users",
    /*
     * Unique constraint on email at the DB level is our last line of defence
     * against duplicate accounts. We also check in the service layer for
     * better error messages, but DB constraints are the authoritative guard.
     */
    uniqueConstraints = {
        @UniqueConstraint(
            name = "uk_users_email",
            columnNames = "email"
        )
    },
    /*
     * Index on email accelerates login lookups (findByEmail) which happen
     * on every authentication request. Without this index, login would
     * require a full table scan on the users table.
     */
    indexes = {
        @Index(name = "idx_users_email", columnList = "email")
    }
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class User extends BaseEntity {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * User's full name. Used for display purposes in the dashboard UI
     * and audit logs. NOT used for authentication.
     */
    @Column(name = "full_name", nullable = false, length = 150)
    private String fullName;

    /**
     * Email serves as the unique login identifier. We chose email over
     * username because:
     *   1. It's inherently unique (no collision resolution needed).
     *   2. It enables password reset flows without a separate identifier.
     *   3. It's what financial professionals expect.
     *
     * The column is lowercased before storage to prevent case-sensitive
     * duplicates (handled in the service layer).
     */
    @Column(name = "email", nullable = false, length = 255, unique = true)
    private String email;

    /**
     * BCrypt-hashed password. NEVER stored in plaintext.
     *
     * BCrypt was chosen over SHA-256 or PBKDF2 because:
     *   1. It has a built-in salt (no separate salt column needed).
     *   2. The work factor is adjustable (we use 10 rounds).
     *   3. It's deliberately slow, making brute-force attacks impractical.
     *
     * Column length 255 to accommodate future algorithm migrations
     * (e.g., Argon2id produces ~97-character hashes).
     */
    @Column(name = "password", nullable = false, length = 255)
    private String password;

    /**
     * User's role in the RBAC system. Stored as a VARCHAR (not ordinal)
     * to avoid silent data corruption if someone reorders the enum values.
     *
     * @Enumerated(EnumType.STRING) ensures "ADMIN" is stored as the
     * literal string "ADMIN" in the database, not as the integer 2.
     * This makes the database human-readable and refactoring-safe.
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "role", nullable = false, length = 20)
    private Role role;

    /**
     * Soft-disable flag. When false, the user cannot authenticate
     * even with valid credentials. This is checked in
     * CustomUserDetailsService via UserDetails.isEnabled().
     *
     * This is distinct from deleting the user record because:
     *   1. Audit trails must reference the user who created transactions.
     *   2. Regulatory requirements may mandate user record retention.
     *   3. Reactivation is a simple flag flip, not a re-creation.
     */
    @Column(name = "is_active", nullable = false)
    @Builder.Default
    private Boolean isActive = true;
}
