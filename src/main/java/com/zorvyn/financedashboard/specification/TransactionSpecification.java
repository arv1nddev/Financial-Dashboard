package com.zorvyn.financedashboard.specification;

import com.zorvyn.financedashboard.entity.Transaction;
import com.zorvyn.financedashboard.entity.TransactionType;
import org.springframework.data.jpa.domain.Specification;

import java.time.LocalDate;

/**
 * ============================================================================
 * TRANSACTION SPECIFICATION — Dynamic Query Composition Engine
 * ============================================================================
 *
 * This class implements the Specification pattern (not the GoF pattern, but
 * Spring Data JPA's adaptation of it). Each static method returns a
 * Specification<Transaction> — a single, composable predicate.
 *
 * The power of this pattern:
 *   Specifications can be combined using .and(), .or(), and .not():
 *
 *     Specification<Transaction> spec = Specification.where(null);
 *     if (category != null) spec = spec.and(hasCategory(category));
 *     if (type != null)     spec = spec.and(hasType(type));
 *     if (from != null)     spec = spec.and(dateAfter(from));
 *     if (to != null)       spec = spec.and(dateBefore(to));
 *     repository.findAll(spec, pageable);
 *
 * This dynamically builds WHERE clauses like:
 *   WHERE category = 'Groceries' AND type = 'EXPENSE'
 *   WHERE transaction_date >= '2026-01-01' AND transaction_date <= '2026-03-31'
 *   WHERE category = 'Salary' AND type = 'INCOME' AND transaction_date >= '2026-01-01'
 *
 * Without Specifications, we'd need a separate repository method for every
 * combination of filters — that's 2^4 = 16 methods for our 4 filter fields.
 * With Specifications, we write 4 methods and compose them at runtime.
 *
 * How it works under the hood:
 *   Each Specification is a lambda that receives a Root (the entity),
 *   a CriteriaQuery (the SELECT), and a CriteriaBuilder (the factory
 *   for creating predicates). It returns a JPA Predicate that Hibernate
 *   translates to SQL.
 *
 * Thread Safety:
 *   All methods are stateless static factories — they're inherently
 *   thread-safe and can be used concurrently from any request thread.
 */
public final class TransactionSpecification {

    /*
     * Private constructor prevents instantiation.
     * This is a utility class — it should only be used via its static methods.
     */
    private TransactionSpecification() {
        throw new UnsupportedOperationException("Utility class — do not instantiate");
    }

    /**
     * Filter by exact category match.
     *
     * Uses CriteriaBuilder.equal() which generates: WHERE category = ?
     * The parameter binding prevents SQL injection automatically.
     *
     * Note: This is case-sensitive. For case-insensitive matching, use
     * CriteriaBuilder.lower() on both sides. We keep it case-sensitive
     * here because categories are normalised (trimmed + title-cased)
     * at the service layer before storage.
     *
     * @param category the category to filter by
     * @return a composable Specification predicate
     */
    public static Specification<Transaction> hasCategory(String category) {
        return (root, query, criteriaBuilder) ->
            criteriaBuilder.equal(root.get("category"), category);
    }

    /**
     * Filter by transaction type (INCOME or EXPENSE).
     *
     * Since type is an enum, Hibernate handles the String-to-enum
     * conversion automatically. The generated SQL compares against
     * the VARCHAR value stored in the DB.
     *
     * @param type the TransactionType to filter by
     * @return a composable Specification predicate
     */
    public static Specification<Transaction> hasType(TransactionType type) {
        return (root, query, criteriaBuilder) ->
            criteriaBuilder.equal(root.get("type"), type);
    }

    /**
     * Filter by transaction date >= the given start date (inclusive).
     *
     * Uses greaterThanOrEqualTo for inclusive range start.
     * Combined with dateBefore(), this creates a closed date range:
     *   WHERE transaction_date >= ? AND transaction_date <= ?
     *
     * @param from the start date (inclusive)
     * @return a composable Specification predicate
     */
    public static Specification<Transaction> dateAfter(LocalDate from) {
        return (root, query, criteriaBuilder) ->
            criteriaBuilder.greaterThanOrEqualTo(root.get("transactionDate"), from);
    }

    /**
     * Filter by transaction date <= the given end date (inclusive).
     *
     * We use lessThanOrEqualTo (not lessThan) for inclusive range end.
     * This matches user expectations: "show me transactions up to March 31"
     * should include transactions ON March 31.
     *
     * @param to the end date (inclusive)
     * @return a composable Specification predicate
     */
    public static Specification<Transaction> dateBefore(LocalDate to) {
        return (root, query, criteriaBuilder) ->
            criteriaBuilder.lessThanOrEqualTo(root.get("transactionDate"), to);
    }

    /**
     * Filter by the user who created the transaction.
     *
     * Used for user-scoped views: "show me only MY transactions".
     * ADMIN users bypass this filter to see all transactions.
     *
     * @param userId the ID of the user who created the transactions
     * @return a composable Specification predicate
     */
    public static Specification<Transaction> createdByUser(Long userId) {
        return (root, query, criteriaBuilder) ->
            criteriaBuilder.equal(root.get("createdByUserId"), userId);
    }
}
