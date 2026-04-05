package com.zorvyn.financedashboard.entity;

/**
 * ============================================================================
 * ROLE ENUMERATION — RBAC Permission Tiers
 * ============================================================================
 *
 * We use a Java enum (mapped to a VARCHAR in PostgreSQL via @Enumerated(STRING))
 * rather than a separate `roles` table for several deliberate reasons:
 *
 *   1. FINITE, KNOWN SET: Our role set is small and rarely changes. A full
 *      many-to-many join table (users ↔ roles) adds unnecessary query
 *      complexity for three values.
 *
 *   2. TYPE SAFETY: Enums give us compile-time exhaustiveness checks in
 *      switch statements and @PreAuthorize expressions. A String column
 *      would allow typos like "ADMN" to slip past silently.
 *
 *   3. PERFORMANCE: No JOINs needed to resolve a user's role — it's a
 *      single column on the users table, indexed and ready.
 *
 * Role Hierarchy (conceptual):
 *   VIEWER  → Can read transactions and view dashboards
 *   ANALYST → VIEWER + can create/update transactions
 *   ADMIN   → ANALYST + can delete transactions + manage users
 *
 * Note: We prefix roles with "ROLE_" when creating Spring Security
 * GrantedAuthority instances, because Spring Security's hasRole() method
 * automatically adds the "ROLE_" prefix. This is a well-known Spring
 * convention that trips up many developers.
 */
public enum Role {

    /**
     * Read-only access. Suitable for stakeholders who need visibility
     * into financial data but should not modify records.
     */
    VIEWER,

    /**
     * Read + Write access. Financial analysts who create and update
     * transactions but cannot perform destructive operations.
     */
    ANALYST,

    /**
     * Full access. System administrators with authority to delete
     * records (soft-delete), manage users, and override restrictions.
     */
    ADMIN
}
