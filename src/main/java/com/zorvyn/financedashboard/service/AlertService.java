package com.zorvyn.financedashboard.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;

/**
 * ============================================================================
 * ALERT SERVICE — Asynchronous Background Event Processing
 * ============================================================================
 *
 * Handles background notifications and alerts triggered by business events.
 * Currently implements a "High Value Transaction Alert" for transactions
 * exceeding $10,000.
 *
 * Why @Async?
 *   Alert processing should NEVER block the HTTP request/response cycle.
 *   A transaction creation should return immediately (200 OK) to the client.
 *   The alert (which might involve email, Slack webhook, audit log entry,
 *   or compliance system notification) runs in a separate thread from our
 *   custom AsyncConfig thread pool.
 *
 * Production Evolution:
 *   In a production system, this service would:
 *   1. Publish to a message queue (RabbitMQ / Kafka) instead of logging
 *   2. Trigger email notifications via SendGrid/SES
 *   3. Create entries in a compliance audit system
 *   4. Send Slack/Teams notifications to the finance team
 *   5. Update a real-time monitoring dashboard
 *
 *   The @Async pattern here is a stepping stone. The method signature
 *   remains the same regardless of the notification mechanism — only
 *   the implementation body changes.
 *
 * Thread Safety:
 *   This service is stateless — all data is passed via method parameters.
 *   No shared mutable state, no synchronization needed.
 */
@Service
public class AlertService {

    private static final Logger log = LoggerFactory.getLogger(AlertService.class);

    /**
     * Threshold for high-value transaction alerts.
     * Defined as a constant for easy tuning. In production, this would
     * be a configurable property (from application.yml or a feature flag).
     */
    private static final BigDecimal HIGH_VALUE_THRESHOLD = new BigDecimal("10000");

    /**
     * Process a high-value transaction alert asynchronously.
     *
     * @Async ensures this method runs in the background thread pool
     * defined in AsyncConfig. The calling thread (servlet request thread)
     * returns immediately without waiting for this method to complete.
     *
     * Important @Async Gotchas:
     *   1. The method must be public (Spring's proxy can't intercept private methods)
     *   2. Self-invocation won't work (calling this from within the same class
     *      bypasses the proxy). That's why this is in a SEPARATE service,
     *      not inside TransactionService.
     *   3. Return type is void (fire-and-forget). For tracked tasks, return
     *      CompletableFuture<Void>.
     *   4. Exceptions in @Async methods are NOT propagated to the caller.
     *      They must be handled within the method or via AsyncUncaughtExceptionHandler.
     *
     * @param transactionId the ID of the created transaction
     * @param amount the transaction amount
     * @param type the transaction type (INCOME/EXPENSE)
     * @param createdByUserId the user who created the transaction
     */
    @Async
    public void processHighValueTransactionAlert(Long transactionId,
                                                  BigDecimal amount,
                                                  String type,
                                                  Long createdByUserId) {
        /*
         * Check if the transaction exceeds the high-value threshold.
         * Using compareTo() instead of > operator because BigDecimal
         * doesn't support arithmetic operators in Java.
         *
         * compareTo() returns:
         *   -1 if amount < threshold
         *    0 if amount == threshold
         *   +1 if amount > threshold
         */
        if (amount.compareTo(HIGH_VALUE_THRESHOLD) > 0) {
            log.warn("""
                ╔══════════════════════════════════════════════════════════╗
                ║  ⚠️  HIGH VALUE TRANSACTION ALERT                       ║
                ╠══════════════════════════════════════════════════════════╣
                ║  Transaction ID  : {}
                ║  Amount           : ${}
                ║  Type             : {}
                ║  Created By User  : {}
                ║  Thread           : {}
                ╠══════════════════════════════════════════════════════════╣
                ║  ACTION: Review required per compliance policy FP-207.  ║
                ║  In production, this triggers:                          ║
                ║    → Email to compliance-team@zorvyn.com                ║
                ║    → Slack alert to #high-value-transactions            ║
                ║    → Audit log entry in compliance system               ║
                ╚══════════════════════════════════════════════════════════╝
                """,
                transactionId, amount, type, createdByUserId,
                Thread.currentThread().getName()
            );

            /*
             * Simulate processing time for the alert.
             * In production, this would be replaced by actual notification calls.
             * The sleep demonstrates that this runs asynchronously —
             * the HTTP response is already sent to the client.
             */
            try {
                Thread.sleep(1000); // Simulate notification processing
                log.info("High-value alert processed successfully for transaction {}", transactionId);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                log.error("Alert processing interrupted for transaction {}", transactionId);
            }
        }
    }
}
