#  Finance Dashboard API

## Overview
This repository contains the backend API for a Finance Dashboard system. It is designed to handle user roles, access control, and financial record management while serving aggregated analytics to a frontend interface. 

The architecture focuses on **clean separation of concerns, robust security, scalability, and developer experience.** It goes beyond basic CRUD by implementing production-ready patterns such as database migrations, rate limiting, soft deletes, and response caching.

## Tech Stack & Architecture
* **Framework:** Spring Boot 3.x
* **Database:** PostgreSQL
* **Caching:** Redis (In-memory aggregation caching)
* **Migrations:** Flyway
* **Security:** Spring Security + JWT (Stateless Authentication)
* **API Documentation:** Springdoc OpenAPI (Swagger UI)
* **Infrastructure:** Docker & Docker Compose

##  Key Features & Engineering Decisions

### 1. Role-Based Access Control (RBAC)
* Implemented stateless JWT authentication.
* Defined three distinct roles: `VIEWER`, `ANALYST`, and `ADMIN`.
* Endpoint access is strictly controlled via method-level security (`@PreAuthorize`).

### 2. Advanced Data Management
* **Soft Deletes:** Financial records are never permanently deleted from the database. Instead, an `is_deleted` flag is toggled to preserve data integrity for auditing purposes.
* **Audit Logging:** Every transaction automatically tracks `created_at`, `updated_at`, `created_by`, and `last_modified_by`.
* **Dynamic Filtering:** The transaction GET endpoint utilizes JPA Specifications to allow dynamic, paginated filtering by date range, category, and transaction type without writing brittle, hard-coded SQL queries.

### 3. High-Performance Dashboard Summaries
* **Redis Caching:** Summary endpoints (e.g., Total Net Balance, Category Totals) are heavily read but infrequently updated. These endpoints are cached in Redis to drastically reduce PostgreSQL load during concurrent dashboard renders.
* **Rate Limiting:** Implemented Bucket4j to rate-limit the summary APIs, preventing excessive hits from degrading application performance.

### 4. Resiliency & Validation
* Comprehensive input validation using Jakarta standard annotations.
* A Global Exception Handler (`@RestControllerAdvice`) intercepts all application errors and translates them into a clean, standardized JSON response format.

##  Getting Started

### Prerequisites
* Docker and Docker Compose installed on your machine.
* Java Development Kit (JDK) 21+.

### Running the Application

1. **Spin up the Infrastructure:**
   Start the PostgreSQL and Redis containers in the background.
   ```bash
   docker-compose up -d