package com.zorvyn.financedashboard.controller;

import com.zorvyn.financedashboard.dto.request.TransactionRequest;
import com.zorvyn.financedashboard.dto.response.TransactionResponse;
import com.zorvyn.financedashboard.entity.TransactionType;
import com.zorvyn.financedashboard.security.CustomUserDetails;
import com.zorvyn.financedashboard.service.TransactionService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;

/**
 * ============================================================================
 * TRANSACTION CONTROLLER — Financial Records CRUD + Filtered Search
 * ============================================================================
 *
 * REST endpoints for managing financial transactions with Role-Based Access:
 *
 *   GET   /transactions      → List (paginated + filtered) [ALL ROLES]
 *   GET   /transactions/{id} → Get single                  [ALL ROLES]
 *   POST  /transactions      → Create                      [ANALYST, ADMIN]
 *   PUT   /transactions/{id} → Update                      [ANALYST, ADMIN]
 *   DELETE /transactions/{id}→ Soft-delete                  [ADMIN only]
 *
 * RBAC Strategy — @PreAuthorize:
 *
 *   We use method-level security (not URL-level) because:
 *   1. Different HTTP methods on the same URL need different roles
 *      (e.g., GET /transactions is for ALL, POST is ANALYST+ADMIN)
 *   2. @PreAuthorize expressions are self-documenting
 *   3. Security logic lives WITH the endpoint, not in a separate config
 *
 *   hasRole('ADMIN') internally checks for authority "ROLE_ADMIN".
 *   hasAnyRole('ANALYST', 'ADMIN') checks for "ROLE_ANALYST" OR "ROLE_ADMIN".
 *
 * @AuthenticationPrincipal:
 *   Spring injects the authenticated user's CustomUserDetails directly
 *   into controller method parameters. This avoids manual SecurityContext
 *   lookups and keeps controllers clean.
 *
 * Pagination:
 *   @PageableDefault provides sensible defaults (page=0, size=20, sorted
 *   by transactionDate DESC). Clients can override with query parameters:
 *     ?page=0&size=10&sort=amount,desc
 */
@RestController
@RequestMapping("/transactions")
@Tag(name = "Transactions", description = "Financial records management — CRUD with dynamic filtering")
public class TransactionController {

    private static final Logger log = LoggerFactory.getLogger(TransactionController.class);

    private final TransactionService transactionService;

    public TransactionController(TransactionService transactionService) {
        this.transactionService = transactionService;
    }

    /**
     * List transactions with dynamic filtering and pagination.
     *
     * All filter parameters are optional. When absent, all transactions
     * are returned (paginated). Filters are AND'd together.
     *
     * Accessible to ALL authenticated roles (VIEWER, ANALYST, ADMIN).
     *
     * Example requests:
     *   GET /transactions?page=0&size=10
     *   GET /transactions?category=Groceries&type=EXPENSE
     *   GET /transactions?from=2026-01-01&to=2026-03-31&sort=amount,desc
     */
    @GetMapping
    @PreAuthorize("hasAnyRole('VIEWER', 'ANALYST', 'ADMIN')")
    @Operation(
        summary = "List transactions",
        description = "Retrieve paginated transactions with optional filters for category, type, and date range."
    )
    public ResponseEntity<Page<TransactionResponse>> getTransactions(
            @Parameter(description = "Filter by category name")
            @RequestParam(required = false) String category,

            @Parameter(description = "Filter by transaction type: INCOME or EXPENSE")
            @RequestParam(required = false) TransactionType type,

            @Parameter(description = "Filter by start date (inclusive, format: yyyy-MM-dd)")
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,

            @Parameter(description = "Filter by end date (inclusive, format: yyyy-MM-dd)")
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,

            @PageableDefault(size = 20, sort = "transactionDate", direction = Sort.Direction.DESC)
            Pageable pageable) {

        log.info("GET /transactions — category: {}, type: {}, from: {}, to: {}, page: {}",
                category, type, from, to, pageable);

        Page<TransactionResponse> response = transactionService.getTransactions(
            category, type, from, to, pageable
        );

        return ResponseEntity.ok(response);
    }

    /**
     * Get a single transaction by ID.
     *
     * Returns 404 if the transaction doesn't exist or is soft-deleted.
     * Accessible to all authenticated roles.
     */
    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('VIEWER', 'ANALYST', 'ADMIN')")
    @Operation(
        summary = "Get transaction by ID",
        description = "Retrieve a single transaction. Soft-deleted transactions return 404."
    )
    public ResponseEntity<TransactionResponse> getTransactionById(
            @PathVariable Long id) {

        log.info("GET /transactions/{}", id);

        TransactionResponse response = transactionService.getTransactionById(id);
        return ResponseEntity.ok(response);
    }

    /**
     * Create a new financial transaction.
     *
     * Restricted to ANALYST and ADMIN roles. VIEWER cannot create transactions.
     *
     * The authenticated user's ID is automatically recorded as the
     * creator (audit trail). If the amount exceeds $10,000, a high-value
     * alert is triggered asynchronously.
     *
     * Returns 201 Created with the full transaction response body.
     *
     * @param request validated transaction data
     * @param userDetails injected by Spring Security from the JWT
     */
    @PostMapping
    @PreAuthorize("hasAnyRole('ANALYST', 'ADMIN')")
    @Operation(
        summary = "Create transaction",
        description = "Create a new financial transaction. Triggers a high-value alert if amount > $10,000."
    )
    public ResponseEntity<TransactionResponse> createTransaction(
            @Valid @RequestBody TransactionRequest request,
            @AuthenticationPrincipal CustomUserDetails userDetails) {

        log.info("POST /transactions — user: {}, type: {}, amount: {}",
                userDetails.getEmail(), request.getType(), request.getAmount());

        TransactionResponse response = transactionService.createTransaction(
            request, userDetails.getId()
        );

        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    /**
     * Update an existing transaction.
     *
     * Restricted to ANALYST and ADMIN roles. Implements PUT semantics
     * (full replacement of all fields).
     *
     * The authenticated user's ID is recorded as the last modifier (audit trail).
     * Returns 200 OK with the updated transaction.
     */
    @PutMapping("/{id}")
    @PreAuthorize("hasAnyRole('ANALYST', 'ADMIN')")
    @Operation(
        summary = "Update transaction",
        description = "Update all fields of an existing transaction. Full replacement (PUT semantics)."
    )
    public ResponseEntity<TransactionResponse> updateTransaction(
            @PathVariable Long id,
            @Valid @RequestBody TransactionRequest request,
            @AuthenticationPrincipal CustomUserDetails userDetails) {

        log.info("PUT /transactions/{} — user: {}", id, userDetails.getEmail());

        TransactionResponse response = transactionService.updateTransaction(
            id, request, userDetails.getId()
        );

        return ResponseEntity.ok(response);
    }

    /**
     * Soft-delete a transaction.
     *
     * Restricted to ADMIN role only. Soft-deleted transactions become
     * invisible to all queries but remain in the database for audit
     * and compliance purposes.
     *
     * Returns 204 No Content (standard for successful DELETE).
     *
     * Why 204 and not 200?
     *   204 No Content means "success, but there's nothing to return".
     *   This is the HTTP standard for DELETE responses. Returning 200 with
     *   a body would imply the deleted resource is still accessible.
     */
    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(
        summary = "Delete transaction (soft)",
        description = "Soft-delete a transaction. Record remains in database for audit but invisible to queries. ADMIN only."
    )
    public ResponseEntity<Void> deleteTransaction(@PathVariable Long id) {
        log.info("DELETE /transactions/{}", id);

        transactionService.deleteTransaction(id);

        return ResponseEntity.noContent().build();
    }
}
