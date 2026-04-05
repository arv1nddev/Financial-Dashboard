package com.zorvyn.financedashboard.dto.response;

import com.zorvyn.financedashboard.entity.TransactionType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

/**
 * ============================================================================
 * TRANSACTION RESPONSE DTO
 * ============================================================================
 *
 * Outbound representation of a Transaction entity. This DTO exists to:
 *
 *   1. DECOUPLE API contract from entity structure: If we refactor the
 *      entity (e.g., split into header + line items), the API response
 *      remains stable. Clients don't break.
 *
 *   2. CONTROL serialization: We explicitly choose which fields to expose.
 *      The entity has isDeleted and internal audit fields that may not
 *      be relevant for all consumers.
 *
 *   3. FLATTEN for readability: The audit timestamps from BaseEntity
 *      appear at the same level as Transaction-specific fields, making
 *      the JSON response flat and easy to consume.
 *
 * Serialization Note:
 *   BigDecimal serializes as a JSON number (not string) by default in
 *   Jackson. This is fine for most clients, but for extreme precision
 *   (e.g., cryptocurrency), consider @JsonSerialize(using = ToStringSerializer)
 *   to send amounts as strings.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TransactionResponse {

    private Long id;
    private BigDecimal amount;
    private TransactionType type;
    private String category;
    private LocalDate transactionDate;
    private String notes;

    // Audit fields — exposed to enable transparency for the end user
    private Long createdByUserId;
    private Long lastModifiedByUserId;
    private Instant createdAt;
    private Instant updatedAt;
}
