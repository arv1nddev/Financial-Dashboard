package com.zorvyn.financedashboard.repository;

import com.zorvyn.financedashboard.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

/**
 * ============================================================================
 * USER REPOSITORY — Data Access Layer for User Entity
 * ============================================================================
 *
 * Extends JpaRepository for standard CRUD + pagination + sorting.
 *
 * We use Spring Data JPA's derived query methods (findByEmail) instead of
 * @Query annotations because:
 *   1. Method names serve as self-documenting queries.
 *   2. Spring validates the method name against the entity metamodel at
 *      startup — typos cause immediate failures, not runtime surprises.
 *   3. The generated SQL is optimised and uses parameter binding
 *      (preventing SQL injection).
 *
 * Note on Optional Return Types:
 *   We return Optional<User> (not User) for findByEmail because a lookup
 *   by email is an inherently "might not exist" operation. This forces
 *   the caller to handle the absent case explicitly, preventing NPEs.
 */
@Repository
public interface UserRepository extends JpaRepository<User, Long> {

    /**
     * Find a user by their email address (case-sensitive).
     *
     * Used by:
     *   - CustomUserDetailsService for authentication
     *   - AuthService for duplicate-email checks during registration
     *
     * Performance: The idx_users_email index on the users table ensures
     * this query is an O(log n) B-tree lookup, not a full table scan.
     *
     * @param email the email address to search for
     * @return Optional containing the user if found, empty otherwise
     */
    Optional<User> findByEmail(String email);

    /**
     * Check if a user with the given email already exists.
     *
     * More efficient than findByEmail().isPresent() because:
     *   - It generates SELECT COUNT(*) or EXISTS, not SELECT * with
     *     full entity hydration.
     *   - No entity object is created in memory.
     *
     * Used during registration to provide a clear "email already taken"
     * error before attempting the insert (which would fail with a
     * DataIntegrityViolationException from the unique constraint).
     *
     * @param email the email address to check
     * @return true if a user with this email exists
     */
    boolean existsByEmail(String email);
}
