package com.zorvyn.financedashboard.repository;

import com.zorvyn.financedashboard.entity.Transaction;
import com.zorvyn.financedashboard.entity.TransactionType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.util.List;

/**
 * ============================================================================
 * TRANSACTION REPOSITORY — Data Access Layer for Financial Records
 * ============================================================================
 *
 * This repository extends TWO interfaces:
 *
 *   1. JpaRepository<Transaction, Long>
 *      → Standard CRUD + batch operations + pagination
 *
 *   2. JpaSpecificationExecutor<Transaction>
 *      → Enables dynamic query composition via JPA Specifications.
 *        This is the key to our filterable GET endpoint where users
 *        can combine date range, category, and type filters dynamically.
 *
 * Why JpaSpecificationExecutor over @Query?
 *   @Query is fine for fixed queries, but our filtering endpoint needs
 *   to compose WHERE clauses dynamically based on which filters the
 *   client provides. With @Query, we'd need 2^N query methods for N
 *   optional filters (or a massive CASE/WHEN in JPQL). Specifications
 *   are composable — each filter is a reusable predicate that can be
 *   AND'd/OR'd together at runtime.
 *
 * Soft Delete Note:
 *   The @SQLRestriction("is_deleted = false") on the Transaction entity
 *   means ALL queries from this repository automatically exclude
 *   soft-deleted records. No WHERE clause needed here. This is the
 *   beauty of Hibernate's @SQLRestriction — it's transparent to the
 *   repository layer.
 */
@Repository
public interface TransactionRepository extends JpaRepository<Transaction, Long>,
                                                JpaSpecificationExecutor<Transaction> {

    // ========================================================================
    // DASHBOARD AGGREGATE QUERIES
    // ========================================================================
    // These use JPQL aggregate functions for efficiency. The database computes
    // SUM/COUNT in a single pass — far more efficient than loading all
    // entities into Java and summing in-memory.
    //
    // COALESCE handles the edge case where there are no transactions of the
    // given type — without it, SUM() returns NULL, which would cause NPEs
    // in the service layer's BigDecimal arithmetic.
    // ========================================================================

    /**
     * Calculate total amount for a specific transaction type.
     *
     * This powers the "Total Income" and "Total Expenses" dashboard cards.
     * COALESCE ensures we return 0.00 instead of null when no transactions
     * exist for the given type.
     *
     * Note: The @SQLRestriction on Transaction entity automatically adds
     * "AND is_deleted = false" to this query.
     */
    @Query("SELECT COALESCE(SUM(t.amount), 0) FROM Transaction t WHERE t.type = :type")
    BigDecimal sumAmountByType(@Param("type") TransactionType type);

    /**
     * Count transactions by type.
     * Used for the dashboard summary statistics.
     */
    long countByType(TransactionType type);

    /**
     * Category-wise aggregation with total and count.
     *
     * Returns a List of Object[] where:
     *   [0] = category (String)
     *   [1] = type (TransactionType)
     *   [2] = total amount (BigDecimal)
     *   [3] = transaction count (Long)
     *
     * GROUP BY produces one row per (category, type) combination.
     * ORDER BY total DESC puts the highest-spending categories first,
     * which is what dashboard users typically want to see.
     *
     * Why not a DTO projection?
     *   JPQL constructor expressions (SELECT new CategoryDTO(...)) work
     *   but couple the query to a specific DTO class. Returning Object[]
     *   and mapping in the service layer gives us more flexibility.
     *   In a production codebase, you might use interface-based projections
     *   for type safety.
     */
    @Query("SELECT t.category, t.type, SUM(t.amount), COUNT(t) " +
           "FROM Transaction t " +
           "GROUP BY t.category, t.type " +
           "ORDER BY SUM(t.amount) DESC")
    List<Object[]> getCategorySummaries();

    /**
     * Count all non-deleted transactions.
     * The @SQLRestriction automatically filters soft-deleted records.
     */
    @Query("SELECT COUNT(t) FROM Transaction t")
    Long countAllTransactions();
}
