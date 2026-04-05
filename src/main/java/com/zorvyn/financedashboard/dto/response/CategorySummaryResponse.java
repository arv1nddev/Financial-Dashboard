package com.zorvyn.financedashboard.dto.response;

import com.zorvyn.financedashboard.entity.TransactionType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.math.BigDecimal;

/**
 * ============================================================================
 * CATEGORY SUMMARY RESPONSE DTO
 * ============================================================================
 *
 * Represents a single row in the "spending by category" breakdown.
 * Used as a nested element within DashboardSummaryResponse.
 *
 * This DTO implements Serializable because it's part of a response
 * that gets cached in Redis. Redis serialization (via Jackson or JDK
 * serializer) requires all nested objects to be serializable.
 *
 * Example JSON output:
 *   {
 *     "category": "Groceries",
 *     "type": "EXPENSE",
 *     "total": 2450.75,
 *     "transactionCount": 34
 *   }
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CategorySummaryResponse implements Serializable {

    private String category;
    private TransactionType type;
    private BigDecimal total;
    private Long transactionCount;
}
