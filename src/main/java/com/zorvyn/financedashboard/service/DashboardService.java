package com.zorvyn.financedashboard.service;

import com.zorvyn.financedashboard.dto.response.CategorySummaryResponse;
import com.zorvyn.financedashboard.dto.response.DashboardSummaryResponse;
import com.zorvyn.financedashboard.entity.TransactionType;
import com.zorvyn.financedashboard.repository.TransactionRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;
import java.util.stream.Collectors;

/**
 * ============================================================================
 * DASHBOARD SERVICE — Aggregated Financial Summary Logic
 * ============================================================================
 *
 * Provides pre-computed financial summaries for the dashboard UI.
 *
 * Performance Strategy — Redis Caching:
 *
 *   Dashboard summaries involve aggregate queries (SUM, GROUP BY, COUNT)
 *   over the entire transactions table. These are expensive operations
 *   that don't need to be repeated on every page load.
 *
 *   @Cacheable("dashboardSummary") caches the result in Redis with a
 *   5-minute TTL (configured in RedisConfig). Subsequent requests within
 *   the TTL window return the cached result without hitting the database.
 *
 *   Cache Invalidation Strategy:
 *   The cache is evicted (cleared) whenever a transaction is created,
 *   updated, or deleted — see @CacheEvict annotations in TransactionService.
 *   This ensures the dashboard always shows accurate data after any write
 *   operation, while still benefiting from caching during read-heavy periods.
 *
 *   Cache Key: Since the dashboard summary is a GLOBAL aggregate (not
 *   per-user), we use a fixed key. If we added per-user summaries, we'd
 *   include the userId in the cache key.
 *
 * Read-Only Transactions:
 *   All methods use @Transactional(readOnly = true) because this service
 *   never modifies data. Read-only transactions:
 *   1. Skip Hibernate dirty-checking (performance boost)
 *   2. Allow read-replica routing in a multi-DB setup
 *   3. Provide a clear intent signal to other developers
 */
@Service
public class DashboardService {

    private static final Logger log = LoggerFactory.getLogger(DashboardService.class);

    private final TransactionRepository transactionRepository;

    public DashboardService(TransactionRepository transactionRepository) {
        this.transactionRepository = transactionRepository;
    }

    /**
     * Get the complete dashboard summary.
     *
     * @Cacheable("dashboardSummary"):
     *   - First call: executes the method body, caches the result in Redis
     *   - Subsequent calls (within TTL): returns cached result, skips method body
     *   - Cache eviction: happens in TransactionService on create/update/delete
     *
     * The default cache key is generated from the method parameters.
     * Since this method takes no parameters, the key is a fixed value —
     * meaning there's ONE cached entry for the entire dashboard.
     *
     * @return aggregated financial summary with category breakdown
     */
    @Cacheable(value = "dashboardSummary")
    @Transactional(readOnly = true)
    public DashboardSummaryResponse getDashboardSummary() {
        log.info("Computing dashboard summary (cache miss — hitting database)");

        /*
         * These three queries execute against the database.
         * After caching, they won't execute again until the cache expires
         * or is evicted.
         *
         * COALESCE in the SQL ensures we get BigDecimal.ZERO instead of null
         * when there are no transactions of a given type.
         */
        BigDecimal totalIncome = transactionRepository.sumAmountByType(TransactionType.INCOME);
        BigDecimal totalExpenses = transactionRepository.sumAmountByType(TransactionType.EXPENSE);
        BigDecimal netBalance = totalIncome.subtract(totalExpenses);
        Long transactionCount = transactionRepository.countAllTransactions();

        // Category-wise breakdown
        List<CategorySummaryResponse> categoryBreakdown = getCategoryBreakdown();

        log.info("Dashboard summary computed — Income: {}, Expenses: {}, Net: {}, Count: {}",
                totalIncome, totalExpenses, netBalance, transactionCount);

        return DashboardSummaryResponse.builder()
                .totalIncome(totalIncome)
                .totalExpenses(totalExpenses)
                .netBalance(netBalance)
                .transactionCount(transactionCount)
                .categoryBreakdown(categoryBreakdown)
                .build();
    }

    /**
     * Get total income (sum of all INCOME transactions).
     *
     * Separate endpoint for clients that only need this single metric
     * (e.g., a mobile widget). Cached independently from the full summary.
     */
    @Cacheable(value = "dashboardSummary", key = "'totalIncome'")
    @Transactional(readOnly = true)
    public BigDecimal getTotalIncome() {
        log.info("Computing total income (cache miss)");
        return transactionRepository.sumAmountByType(TransactionType.INCOME);
    }

    /**
     * Get total expenses (sum of all EXPENSE transactions).
     */
    @Cacheable(value = "dashboardSummary", key = "'totalExpenses'")
    @Transactional(readOnly = true)
    public BigDecimal getTotalExpenses() {
        log.info("Computing total expenses (cache miss)");
        return transactionRepository.sumAmountByType(TransactionType.EXPENSE);
    }

    /**
     * Get net balance (income minus expenses).
     */
    @Cacheable(value = "dashboardSummary", key = "'netBalance'")
    @Transactional(readOnly = true)
    public BigDecimal getNetBalance() {
        log.info("Computing net balance (cache miss)");
        BigDecimal income = transactionRepository.sumAmountByType(TransactionType.INCOME);
        BigDecimal expenses = transactionRepository.sumAmountByType(TransactionType.EXPENSE);
        return income.subtract(expenses);
    }

    /**
     * Get category-wise breakdown of income and expenses.
     *
     * This method maps the raw Object[] results from the JPQL query
     * into strongly-typed CategorySummaryResponse DTOs.
     *
     * The repository returns Object[] because JPQL aggregate queries
     * can't directly construct DTOs without a constructor expression.
     * We map here in the service layer for flexibility.
     */
    @Cacheable(value = "dashboardSummary", key = "'categoryBreakdown'")
    @Transactional(readOnly = true)
    public List<CategorySummaryResponse> getCategoryBreakdown() {
        log.info("Computing category breakdown (cache miss)");

        return transactionRepository.getCategorySummaries().stream()
                .map(row -> CategorySummaryResponse.builder()
                        .category((String) row[0])
                        .type((TransactionType) row[1])
                        .total((BigDecimal) row[2])
                        .transactionCount((Long) row[3])
                        .build())
                .collect(Collectors.toList());
    }
}
