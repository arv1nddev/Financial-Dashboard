package com.zorvyn.financedashboard.security;

import com.zorvyn.financedashboard.entity.User;
import com.zorvyn.financedashboard.repository.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * ============================================================================
 * CUSTOM USER DETAILS SERVICE — User Loading for Spring Security
 * ============================================================================
 *
 * This service is the bridge between our UserRepository (data access) and
 * Spring Security's authentication pipeline. When a user attempts to log
 * in or when a JWT is validated, Spring Security calls loadUserByUsername()
 * to fetch the user's credentials and authorities.
 *
 * Why a custom implementation?
 *   Spring provides InMemoryUserDetailsManager and JdbcUserDetailsManager,
 *   but neither works with our JPA-managed User entity. This custom
 *   implementation gives us full control over:
 *   1. How users are fetched (via our UserRepository)
 *   2. How domain Users map to Spring Security UserDetails
 *   3. Error messages for missing users
 *
 * Transaction Management:
 *   @Transactional(readOnly = true) is critical here because this method
 *   is called on EVERY authenticated request (via the JWT filter). Read-only
 *   transactions:
 *   1. Skip dirty-checking (performance boost)
 *   2. May use read replicas in a multi-DB setup
 *   3. Signal to Hibernate that no flush is needed
 */
@Service
public class CustomUserDetailsService implements UserDetailsService {

    private static final Logger log = LoggerFactory.getLogger(CustomUserDetailsService.class);

    private final UserRepository userRepository;

    public CustomUserDetailsService(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    /**
     * Load user by email (we use email as the "username" in our system).
     *
     * This method is called by:
     *   1. DaoAuthenticationProvider during login (password verification)
     *   2. JwtAuthenticationFilter on every request (token → user lookup)
     *
     * @param email the user's email address (used as username)
     * @return UserDetails containing credentials and authorities
     * @throws UsernameNotFoundException if no user with this email exists
     */
    @Override
    @Transactional(readOnly = true)
    public UserDetails loadUserByUsername(String email) throws UsernameNotFoundException {
        log.debug("Loading user details for email: {}", email);

        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> {
                    log.warn("Authentication attempt for non-existent email: {}", email);
                    /*
                     * The exception message here is internal — it's caught by
                     * Spring Security and converted to a generic "Bad credentials"
                     * response. We log the specific email for debugging but
                     * never expose it to the client (preventing user enumeration).
                     */
                    return new UsernameNotFoundException(
                        "User not found with email: " + email
                    );
                });

        return CustomUserDetails.fromUser(user);
    }
}
