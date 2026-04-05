package com.zorvyn.financedashboard.service;

import com.zorvyn.financedashboard.dto.request.TransactionRequest;
import com.zorvyn.financedashboard.dto.response.TransactionResponse;
import com.zorvyn.financedashboard.entity.Transaction;
import com.zorvyn.financedashboard.entity.TransactionType;
import com.zorvyn.financedashboard.exception.ResourceNotFoundException;
import com.zorvyn.financedashboard.repository.TransactionRepository;
import com.zorvyn.financedashboard.specification.TransactionSpecification;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;

/**
 * ============================================================================
 * TRANSACTION SERVICE — Financial Records Business Logic
 * ============================================================================
 *
 * Manages the full lifecycle of financial transactions: create, read,
 * update, delete (soft), and filtered search.
 *
 * Architecture Notes:
 *
 *   1. DTO ↔ ENTITY MAPPING: This service handles the mapping between
 *      request DTOs and entity objects. In a larger system, you'd extract
 *      this into a dedicated MapperService or use MapStruct. For this
 *      scope, inline mapping keeps the code navigable.
 *
 *   2. CACHE EVICTION: Every write operation (create, update, delete)
 *      evicts the dashboard summary cache. This ensures the dashboard
 *      reflects the latest data after any modification. The @CacheEvict
 *      annotation on write methods clears the "dashboardSummary" cache.
 *
 *   3. AUDIT FIELDS: createdByUserId and lastModifiedByUserId are set
 *      here in the service layer (not via JPA AuditingEntityListener)
 *      because they come from the SecurityContext, not from JPA's built-in
 *      auditing. JPA's @CreatedBy requires a custom AuditorAware bean,
 *      which adds complexity for marginal benefit.
 *
 *   4. ASYNC ALERTS: High-value transactions trigger an @Async alert
 *      in a separate service. The alert runs in a background thread —
 *      the HTTP response is returned immediately.
 */
@Service
public class TransactionService {

    private static final Logger log = LoggerFactory.getLogger(TransactionService.class);

    private final TransactionRepository transactionRepository;
    private final AlertService alertService;

    public TransactionService(TransactionRepository transactionRepository,
                               AlertService alertService) {
        this.transactionRepository = transactionRepository;
        this.alertService = alertService;
    }

    /**
     * Create a new financial transaction.
     *
     * Flow:
     *   1. Map DTO → Entity
     *   2. Set audit fields (who created it)
     *   3. Persist to database
     *   4. Trigger high-value alert if amount > $10,000 (async)
     *   5. Evict dashboard cache (data changed)
     *   6. Return response DTO
     *
     * @CacheEvict clears the dashboard summary cache because the summary
     * aggregates (total income/expenses) are now stale after this insert.
     * We evict ALL entries in the cache (allEntries=true) because the
     * summary is a global aggregate, not user-specific.
     *
     * @param request validated transaction data
     * @param userId the authenticated user's ID (from JWT)
     * @return the created transaction as a response DTO
     */
    @Transactional
    @CacheEvict(value = "dashboardSummary", allEntries = true)
    public TransactionResponse createTransaction(TransactionRequest request, Long userId) {
        log.info("Creating transaction: type={}, amount={}, category={}, user={}",
                request.getType(), request.getAmount(), request.getCategory(), userId);

        Transaction transaction = Transaction.builder()
                .amount(request.getAmount())
                .type(request.getType())
                .category(request.getCategory().trim()) // Normalise category
                .transactionDate(request.getTransactionDate())
                .notes(request.getNotes())
                .createdByUserId(userId)
                .lastModifiedByUserId(userId)
                .isDeleted(false)
                .build();

        Transaction saved = transactionRepository.save(transaction);

        log.info("Transaction created with ID: {}", saved.getId());

        /*
         * Trigger high-value alert ASYNCHRONOUSLY.
         * This call returns immediately — the alert processing happens
         * in a background thread from our custom thread pool.
         *
         * Why call alertService instead of an internal method?
         *   @Async only works when called through a Spring proxy (i.e.,
         *   from a DIFFERENT bean). Self-invocation (calling an @Async
         *   method within the same class) bypasses the proxy and runs
         *   synchronously. This is a well-known Spring AOP limitation.
         */
        alertService.processHighValueTransactionAlert(
                saved.getId(),
                saved.getAmount(),
                saved.getType().name(),
                userId
        );

        return mapToResponse(saved);
    }

    /**
     * Retrieve a single transaction by ID.
     *
     * Soft-deleted transactions are automatically excluded by Hibernate's
     * @SQLRestriction on the Transaction entity. If the ID exists but is
     * soft-deleted, this will throw ResourceNotFoundException — which is
     * the correct behaviour (deleted resources should be invisible).
     *
     * @param id the transaction ID
     * @return the transaction as a response DTO
     * @throws ResourceNotFoundException if the transaction doesn't exist
     */
    @Transactional(readOnly = true)
    public TransactionResponse getTransactionById(Long id) {
        Transaction transaction = transactionRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Transaction", "id", id));

        return mapToResponse(transaction);
    }

    /**
     * Retrieve transactions with dynamic filtering and pagination.
     *
     * This is the "Wow Factor" endpoint — it composes JPA Specifications
     * dynamically based on which filter parameters the client provides.
     *
     * Each filter is optional. Absent filters are simply not applied:
     *   GET /transactions?page=0&size=10                       → all transactions
     *   GET /transactions?category=Groceries&page=0&size=10    → by category
     *   GET /transactions?type=EXPENSE&from=2026-01-01         → by type + date
     *   GET /transactions?from=2026-01-01&to=2026-03-31        → by date range
     *
     * The Specification pattern makes this O(1) in method complexity —
     * we don't need separate repository methods for each filter combination.
     *
     * @param category optional category filter
     * @param type optional transaction type filter
     * @param from optional start date (inclusive)
     * @param to optional end date (inclusive)
     * @param pageable pagination and sorting parameters
     * @return a Page of transaction response DTOs
     */
    @Transactional(readOnly = true)
    public Page<TransactionResponse> getTransactions(String category,
                                                      TransactionType type,
                                                      LocalDate from,
                                                      LocalDate to,
                                                      Pageable pageable) {
        /*
         * Start with a null Specification (matches everything).
         * Specification.where(null) creates a "true" predicate.
         * Then conditionally AND each filter.
         */
        Specification<Transaction> spec = Specification.where(null);

        if (category != null && !category.isBlank()) {
            spec = spec.and(TransactionSpecification.hasCategory(category.trim()));
        }
        if (type != null) {
            spec = spec.and(TransactionSpecification.hasType(type));
        }
        if (from != null) {
            spec = spec.and(TransactionSpecification.dateAfter(from));
        }
        if (to != null) {
            spec = spec.and(TransactionSpecification.dateBefore(to));
        }

        log.debug("Querying transactions with filters — category: {}, type: {}, from: {}, to: {}",
                category, type, from, to);

        /*
         * JpaSpecificationExecutor.findAll(spec, pageable) generates a single
         * SQL query with all filter clauses AND pagination LIMIT/OFFSET.
         * This is far more efficient than loading all records and filtering
         * in Java memory.
         */
        return transactionRepository.findAll(spec, pageable)
                .map(this::mapToResponse);
    }

    /**
     * Update an existing transaction.
     *
     * Implements a full replacement (PUT semantics) — all fields are
     * overwritten from the request DTO. For partial updates, you'd
     * implement a PATCH endpoint with null-checking on each field.
     *
     * Cache eviction ensures dashboard summaries reflect the updated amounts.
     *
     * @param id the transaction ID to update
     * @param request the new transaction data
     * @param userId the authenticated user's ID (for audit trail)
     * @return the updated transaction
     * @throws ResourceNotFoundException if the transaction doesn't exist
     */
    @Transactional
    @CacheEvict(value = "dashboardSummary", allEntries = true)
    public TransactionResponse updateTransaction(Long id, TransactionRequest request, Long userId) {
        Transaction transaction = transactionRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Transaction", "id", id));

        // Update all fields from the request DTO
        transaction.setAmount(request.getAmount());
        transaction.setType(request.getType());
        transaction.setCategory(request.getCategory().trim());
        transaction.setTransactionDate(request.getTransactionDate());
        transaction.setNotes(request.getNotes());
        transaction.setLastModifiedByUserId(userId);

        Transaction updated = transactionRepository.save(transaction);

        log.info("Transaction {} updated by user {}", id, userId);

        // Check if the updated amount triggers a high-value alert
        alertService.processHighValueTransactionAlert(
                updated.getId(),
                updated.getAmount(),
                updated.getType().name(),
                userId
        );

        return mapToResponse(updated);
    }

    /**
     * Soft-delete a transaction.
     *
     * The @SQLDelete annotation on the Transaction entity intercepts
     * Hibernate's DELETE and converts it to:
     *   UPDATE transactions SET is_deleted = true WHERE id = ?
     *
     * The record remains in the database for audit purposes but becomes
     * invisible to all queries via @SQLRestriction("is_deleted = false").
     *
     * Cache eviction ensures dashboard summaries exclude the deleted transaction.
     *
     * @param id the transaction ID to soft-delete
     * @throws ResourceNotFoundException if the transaction doesn't exist
     */
    @Transactional
    @CacheEvict(value = "dashboardSummary", allEntries = true)
    public void deleteTransaction(Long id) {
        Transaction transaction = transactionRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Transaction", "id", id));

        /*
         * This calls repository.delete() which generates a DELETE SQL.
         * But the @SQLDelete annotation on Transaction intercepts it and
         * executes UPDATE ... SET is_deleted = true instead.
         *
         * The developer experience is clean: we write delete semantics,
         * Hibernate handles the soft-delete rewrite transparently.
         */
        transactionRepository.delete(transaction);

        log.info("Transaction {} soft-deleted", id);
    }

    // ========================================================================
    // MAPPING HELPERS
    // ========================================================================

    /**
     * Map a Transaction entity to a TransactionResponse DTO.
     *
     * Why not use MapStruct?
     *   MapStruct is excellent for large projects with many entities/DTOs.
     *   For this project with two entity types, manual mapping is more
     *   transparent and avoids an additional annotation processor dependency.
     *   The mapping logic is trivial and unlikely to cause bugs.
     */
    private TransactionResponse mapToResponse(Transaction transaction) {
        return TransactionResponse.builder()
                .id(transaction.getId())
                .amount(transaction.getAmount())
                .type(transaction.getType())
                .category(transaction.getCategory())
                .transactionDate(transaction.getTransactionDate())
                .notes(transaction.getNotes())
                .createdByUserId(transaction.getCreatedByUserId())
                .lastModifiedByUserId(transaction.getLastModifiedByUserId())
                .createdAt(transaction.getCreatedAt())
                .updatedAt(transaction.getUpdatedAt())
                .build();
    }
}
