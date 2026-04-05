package com.zorvyn.financedashboard.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.List;

/**
 * ============================================================================
 * DASHBOARD SUMMARY RESPONSE DTO
 * ============================================================================
 *
 * Aggregated financial overview for the dashboard UI. Combines multiple
 * expensive aggregate queries into a single response object, which is
 * then cached in Redis to avoid repeated computation.
 *
 * Caching Strategy:
 *   This response is cached with a 5-minute TTL. The trade-off:
 *   - PRO: Dashboard loads are near-instant (sub-5ms from Redis vs.
 *          100-500ms for aggregate queries on large datasets)
 *   - CON: Data can be up to 5 minutes stale
 *   - MITIGATION: Cache is evicted on transaction create/update/delete,
 *     so the staleness window only applies when OTHER users modify data.
 *
 * All monetary values use BigDecimal for exact arithmetic.
 * Net balance = totalIncome - totalExpenses (computed in the service layer).
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DashboardSummaryResponse {

    /**
     * Sum of all INCOME-type transactions.
     * Default to ZERO (not null) to prevent NPEs in frontend calculations.
     */
    @Builder.Default
    private BigDecimal totalIncome = BigDecimal.ZERO;

    /**
     * Sum of all EXPENSE-type transactions.
     */
    @Builder.Default
    private BigDecimal totalExpenses = BigDecimal.ZERO;

    /**
     * Net balance = totalIncome - totalExpenses.
     * A negative value indicates the user is spending more than earning.
     */
    @Builder.Default
    private BigDecimal netBalance = BigDecimal.ZERO;

    /**
     * Total number of transactions (for display/context).
     */
    private Long transactionCount;

    /**
     * Breakdown of totals by category.
     * Each entry contains the category name, its total amount, and
     * the transaction type. This powers the "spending by category"
     * charts in the dashboard UI.
     */
    private List<CategorySummaryResponse> categoryBreakdown;
}
