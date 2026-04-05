package com.zorvyn.financedashboard.dto.request;

import com.zorvyn.financedashboard.entity.TransactionType;
import jakarta.validation.constraints.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * ============================================================================
 * TRANSACTION REQUEST DTO — Create/Update Financial Record
 * ============================================================================
 *
 * Validation Strategy:
 *   We validate at the DTO boundary (not the entity) because:
 *   1. Error messages are user-facing and should be descriptive.
 *   2. Entities should represent valid domain state, not validate input.
 *   3. Different operations (create vs. update) may have different rules
 *      (e.g., partial updates via PATCH don't require all fields).
 *
 * Money Validation:
 *   @DecimalMin("0.01") ensures no zero or negative amounts. In a real
 *   system, you might allow zero-amount transactions for fee waivers,
 *   but that's a business rule that should be explicitly discussed.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TransactionRequest {

    /**
     * Transaction amount. Must be positive and limited to 19 digits
     * total with 4 decimal places (matching the DB column definition).
     *
     * @DecimalMin("0.01") rejects zero-value transactions.
     * @Digits constrains to NUMERIC(19,4) precision to prevent DB overflow.
     */
    @NotNull(message = "Amount is required")
    @DecimalMin(value = "0.01", message = "Amount must be greater than zero")
    @Digits(integer = 15, fraction = 4, message = "Amount must have at most 15 integer digits and 4 decimal places")
    private BigDecimal amount;

    /**
     * Transaction type: INCOME or EXPENSE.
     * @NotNull because the dashboard summary calculations depend on
     * every transaction having a type classification.
     */
    @NotNull(message = "Transaction type is required (INCOME or EXPENSE)")
    private TransactionType type;

    /**
     * Descriptive category label. Trim whitespace in the service layer
     * to prevent "Groceries" and " Groceries " from being treated as
     * different categories in GROUP BY aggregations.
     */
    @NotBlank(message = "Category is required")
    @Size(max = 100, message = "Category must not exceed 100 characters")
    private String category;

    /**
     * Business date of the transaction. We accept past and present dates
     * but reject future dates (financial records should reflect actual events).
     * The @PastOrPresent annotation handles this.
     */
    @NotNull(message = "Transaction date is required")
    @PastOrPresent(message = "Transaction date cannot be in the future")
    private LocalDate transactionDate;

    /**
     * Optional notes/description. Limited to 2000 chars to prevent
     * abuse while allowing meaningful descriptions.
     */
    @Size(max = 2000, message = "Notes must not exceed 2000 characters")
    private String notes;
}
