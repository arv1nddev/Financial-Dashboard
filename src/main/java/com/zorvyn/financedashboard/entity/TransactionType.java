package com.zorvyn.financedashboard.entity;

/**
 * ============================================================================
 * TRANSACTION TYPE ENUMERATION
 * ============================================================================
 *
 * Binary classification of financial transactions. This enum drives:
 *   - Dashboard summary calculations (total income vs. total expenses)
 *   - Dynamic filtering via JPA Specifications
 *   - Report generation and category-wise breakdowns
 *
 * Why only two types?
 *   In double-entry bookkeeping, every transaction is fundamentally either
 *   a debit or a credit. INCOME and EXPENSE map to this duality while
 *   remaining accessible to non-accounting users. If the system evolves
 *   to need TRANSFER, REFUND, or ADJUSTMENT types, this enum is the
 *   single point of extension — all downstream logic (Specifications,
 *   repository queries, DTOs) will get compile-time errors until updated.
 */
public enum TransactionType {

    /**
     * Money flowing into the account — salary, dividends, refunds, etc.
     */
    INCOME,

    /**
     * Money flowing out of the account — purchases, bills, taxes, etc.
     */
    EXPENSE
}
