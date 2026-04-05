package com.zorvyn.financedashboard.security;

import com.zorvyn.financedashboard.entity.User;
import lombok.AllArgsConstructor;
import lombok.Getter;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import java.util.Collection;
import java.util.List;

/**
 * ============================================================================
 * CUSTOM USER DETAILS — Bridge Between Domain User and Spring Security
 * ============================================================================
 *
 * Spring Security requires a UserDetails implementation to authenticate
 * and authorise requests. Rather than making our User entity implement
 * UserDetails directly, we create this adapter class.
 *
 * Why a separate class instead of User implements UserDetails?
 *
 *   1. SEPARATION OF CONCERNS: The User entity belongs to the domain
 *      layer. UserDetails is a Spring Security contract. Mixing them
 *      would couple our domain model to Spring Security's interface,
 *      violating Clean Architecture's dependency rule.
 *
 *   2. FLEXIBILITY: If we change our User entity (e.g., add fields,
 *      change the role model), the UserDetails contract is isolated.
 *
 *   3. TESTABILITY: We can unit-test security logic with a mock
 *      CustomUserDetails without needing a JPA-managed User entity.
 *
 * Role Prefix Convention:
 *   Spring Security expects authorities to start with "ROLE_" when
 *   using hasRole("ADMIN") in @PreAuthorize expressions. The
 *   SimpleGrantedAuthority("ROLE_ADMIN") matches hasRole("ADMIN").
 *   If we used hasAuthority("ADMIN"), we wouldn't need the prefix,
 *   but hasRole() is more readable in annotations.
 */
@Getter
@AllArgsConstructor
public class CustomUserDetails implements UserDetails {

    private final Long id;
    private final String fullName;
    private final String email;
    private final String password;
    private final boolean active;
    private final Collection<? extends GrantedAuthority> authorities;

    /**
     * Factory method to create CustomUserDetails from a User entity.
     *
     * This is the only place where we convert between domain and security
     * models. If the User entity changes, only this method needs updating.
     *
     * @param user the domain User entity (loaded from database)
     * @return a Spring Security-compatible UserDetails instance
     */
    public static CustomUserDetails fromUser(User user) {
        /*
         * We wrap the role in "ROLE_" prefix for Spring Security's hasRole()
         * method. Example: Role.ADMIN → "ROLE_ADMIN"
         *
         * List.of() creates an immutable single-element list because our
         * current model assigns one role per user. If we add multi-role
         * support later, this becomes a Stream mapping operation.
         */
        List<GrantedAuthority> authorities = List.of(
            new SimpleGrantedAuthority("ROLE_" + user.getRole().name())
        );

        return new CustomUserDetails(
            user.getId(),
            user.getFullName(),
            user.getEmail(),
            user.getPassword(),
            user.getIsActive(),
            authorities
        );
    }

    // ========================================================================
    // UserDetails Contract Implementation
    // ========================================================================

    @Override
    public String getUsername() {
        // We use email as the username (login identifier)
        return email;
    }

    @Override
    public String getPassword() {
        return password;
    }

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        return authorities;
    }

    /**
     * Account is considered active based on the User.isActive flag.
     * When false, Spring Security will reject authentication with
     * "User account is disabled" — preventing deactivated users from
     * logging in even with valid credentials.
     */
    @Override
    public boolean isEnabled() {
        return active;
    }

    /**
     * We don't implement account expiry, credential expiry, or
     * account locking in this version. Returning true for all
     * of them means these checks pass unconditionally.
     *
     * In a production system, you might implement:
     *   - isAccountNonExpired() → for time-limited contractor accounts
     *   - isCredentialsNonExpired() → for mandatory password rotation
     *   - isAccountNonLocked() → for brute-force protection (lock after N failures)
     */
    @Override
    public boolean isAccountNonExpired() {
        return true;
    }

    @Override
    public boolean isAccountNonLocked() {
        return true;
    }

    @Override
    public boolean isCredentialsNonExpired() {
        return true;
    }
}
