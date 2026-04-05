package com.zorvyn.financedashboard;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;
import org.springframework.scheduling.annotation.EnableAsync;

/**
 * ============================================================================
 * FINANCE DASHBOARD API — Application Entry Point
 * ============================================================================
 *
 * Architectural Overview:
 * This application follows a Clean Architecture / N-Tier pattern:
 *
 *   ┌─────────────────────────────────────────────────┐
 *   │  PRESENTATION LAYER (Controllers, DTOs)         │
 *   ├─────────────────────────────────────────────────┤
 *   │  APPLICATION LAYER (Services, Specifications)   │
 *   ├─────────────────────────────────────────────────┤
 *   │  DOMAIN LAYER (Entities, Enums, Exceptions)     │
 *   ├─────────────────────────────────────────────────┤
 *   │  INFRASTRUCTURE LAYER (Repos, Config, Security) │
 *   └─────────────────────────────────────────────────┘
 *
 * Key Annotations:
 *   @EnableCaching     → Activates Spring's cache abstraction for @Cacheable
 *   @EnableJpaAuditing → Powers @CreatedDate / @LastModifiedDate on entities
 *   @EnableAsync        → Enables @Async for background processing (alerts)
 *
 * Why these are on the main class:
 *   These cross-cutting concerns affect the entire application context.
 *   Placing them here makes it immediately clear to any developer that
 *   caching, auditing, and async processing are foundational features,
 *   not optional add-ons buried in some config class.
 *
 * @author Zorvyn Engineering
 * @version 1.0.0
 */
@SpringBootApplication
@EnableCaching
@EnableJpaAuditing
@EnableAsync
public class FinanceDashboardApplication {

    public static void main(String[] args) {
        SpringApplication.run(FinanceDashboardApplication.class, args);
    }
}
