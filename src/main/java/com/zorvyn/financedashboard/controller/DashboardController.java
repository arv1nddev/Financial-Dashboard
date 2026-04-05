package com.zorvyn.financedashboard.controller;

import com.zorvyn.financedashboard.dto.response.CategorySummaryResponse;
import com.zorvyn.financedashboard.dto.response.DashboardSummaryResponse;
import com.zorvyn.financedashboard.service.DashboardService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.util.List;

/**
 * ============================================================================
 * DASHBOARD CONTROLLER — Financial Summary Endpoints
 * ============================================================================
 *
 * Pre-computed aggregate endpoints for the dashboard UI.
 *
 * Performance Characteristics:
 *   These endpoints are backed by Redis caching (@Cacheable in DashboardService)
 *   AND protected by Bucket4j rate limiting (via RateLimiterInterceptor).
 *
 *   First request (cache miss): ~100-500ms (database aggregate queries)
 *   Subsequent requests (cache hit): ~1-5ms (Redis lookup)
 *   After rate limit: 429 Too Many Requests (0ms, no DB hit)
 *
 * Rate Limiting:
 *   The /dashboard/** path pattern is registered in WebMvcConfig with
 *   the RateLimiterInterceptor. Default: 20 requests/minute per client IP.
 *
 * Access Control:
 *   All dashboard endpoints are accessible to any authenticated user
 *   (VIEWER, ANALYST, ADMIN). The dashboard is a read-only view.
 */
@RestController
@RequestMapping("/dashboard")
@Tag(name = "Dashboard", description = "Financial summary endpoints — cached with Redis, rate-limited")
public class DashboardController {

    private static final Logger log = LoggerFactory.getLogger(DashboardController.class);

    private final DashboardService dashboardService;

    public DashboardController(DashboardService dashboardService) {
        this.dashboardService = dashboardService;
    }

    /**
     * Get the complete dashboard summary.
     *
     * Returns total income, total expenses, net balance, transaction count,
     * and a category-wise breakdown — all in a single response.
     *
     * This is the primary endpoint for the dashboard page. It aggregates
     * multiple metrics into one call to minimise frontend HTTP requests.
     *
     * Cached for 5 minutes. Evicted when any transaction is modified.
     */
    @GetMapping("/summary")
    @PreAuthorize("hasAnyRole('VIEWER', 'ANALYST', 'ADMIN')")
    @Operation(
        summary = "Get full dashboard summary",
        description = """
            Returns aggregated financial metrics: total income, total expenses,
            net balance, transaction count, and category-wise breakdown.
            
            **Caching**: Results are cached in Redis for 5 minutes.
            **Rate Limit**: 20 requests/minute per client IP.
            """
    )
    public ResponseEntity<DashboardSummaryResponse> getDashboardSummary() {
        log.info("GET /dashboard/summary");
        DashboardSummaryResponse response = dashboardService.getDashboardSummary();
        return ResponseEntity.ok(response);
    }

    /**
     * Get total income only.
     * Lightweight endpoint for widgets that only need this single metric.
     */
    @GetMapping("/income")
    @PreAuthorize("hasAnyRole('VIEWER', 'ANALYST', 'ADMIN')")
    @Operation(
        summary = "Get total income",
        description = "Returns the sum of all INCOME-type transactions. Cached for 5 minutes."
    )
    public ResponseEntity<BigDecimal> getTotalIncome() {
        log.info("GET /dashboard/income");
        return ResponseEntity.ok(dashboardService.getTotalIncome());
    }

    /**
     * Get total expenses only.
     */
    @GetMapping("/expenses")
    @PreAuthorize("hasAnyRole('VIEWER', 'ANALYST', 'ADMIN')")
    @Operation(
        summary = "Get total expenses",
        description = "Returns the sum of all EXPENSE-type transactions. Cached for 5 minutes."
    )
    public ResponseEntity<BigDecimal> getTotalExpenses() {
        log.info("GET /dashboard/expenses");
        return ResponseEntity.ok(dashboardService.getTotalExpenses());
    }

    /**
     * Get net balance (income - expenses).
     */
    @GetMapping("/balance")
    @PreAuthorize("hasAnyRole('VIEWER', 'ANALYST', 'ADMIN')")
    @Operation(
        summary = "Get net balance",
        description = "Returns total income minus total expenses. Negative means spending exceeds income."
    )
    public ResponseEntity<BigDecimal> getNetBalance() {
        log.info("GET /dashboard/balance");
        return ResponseEntity.ok(dashboardService.getNetBalance());
    }

    /**
     * Get category-wise breakdown.
     *
     * Returns a list of categories with their total amounts and counts,
     * sorted by total amount descending (highest-spending categories first).
     */
    @GetMapping("/categories")
    @PreAuthorize("hasAnyRole('VIEWER', 'ANALYST', 'ADMIN')")
    @Operation(
        summary = "Get category breakdown",
        description = "Returns totals grouped by category and transaction type, sorted by amount descending."
    )
    public ResponseEntity<List<CategorySummaryResponse>> getCategoryBreakdown() {
        log.info("GET /dashboard/categories");
        return ResponseEntity.ok(dashboardService.getCategoryBreakdown());
    }
}
