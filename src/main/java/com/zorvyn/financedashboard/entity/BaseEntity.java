package com.zorvyn.financedashboard.entity;

import jakarta.persistence.*;
import lombok.*;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.io.Serializable;
import java.time.Instant;

/**
 * ============================================================================
 * BASE ENTITY — Mapped Superclass for Audit Fields
 * ============================================================================
 *
 * This abstract class implements the "Audit Trail" pattern using JPA's
 * @MappedSuperclass. Every entity in the system inherits:
 *   - id         → Auto-generated primary key (IDENTITY strategy)
 *   - createdAt  → Timestamp of record creation (immutable)
 *   - updatedAt  → Timestamp of last modification (auto-updated)
 *
 * Why @MappedSuperclass over @Inheritance?
 *   We're not modelling an IS-A hierarchy that needs polymorphic queries.
 *   @MappedSuperclass simply injects common columns into each child table
 *   without creating a discriminator column or join tables. This is the
 *   correct choice for cross-cutting audit concerns.
 *
 * Why Instant over LocalDateTime?
 *   Financial systems MUST store timestamps in UTC to avoid timezone bugs.
 *   java.time.Instant is inherently UTC — there's no timezone to
 *   accidentally misinterpret. LocalDateTime is timezone-agnostic and
 *   has caused countless production bugs in financial applications.
 *
 * Why IDENTITY over SEQUENCE?
 *   IDENTITY (auto-increment) is simpler and sufficient for our single-DB
 *   architecture. In a distributed or high-throughput system, SEQUENCE
 *   with allocation-size > 1 would reduce round-trips for bulk inserts.
 *   We can switch strategies later without changing any business logic.
 *
 * @see AuditingEntityListener — Spring Data JPA's listener that populates
 *      @CreatedDate and @LastModifiedDate automatically.
 */
@MappedSuperclass
@EntityListeners(AuditingEntityListener.class)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public abstract class BaseEntity implements Serializable {

    /**
     * Primary key. Using Long (not UUID) because:
     *   1. 8-byte BIGINT is more performant for B-tree indexing than 16-byte UUID.
     *   2. Sequential IDs improve index locality and insert performance.
     *   3. We're a monolithic API — we don't need globally unique IDs across services.
     *
     * In a microservices context, we'd switch to UUID v7 (time-sortable) to
     * avoid conflicts between services generating IDs independently.
     */
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /**
     * Record creation timestamp. Set once by the JPA auditing listener
     * and never modified again (ensured by updatable=false).
     *
     * Column is NOT NULL because every record must have a creation timestamp
     * for regulatory compliance and audit trails in financial systems.
     */
    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    /**
     * Last modification timestamp. Automatically updated by the JPA
     * auditing listener on every persist/merge operation.
     */
    @LastModifiedDate
    @Column(name = "updated_at")
    private Instant updatedAt;
}
