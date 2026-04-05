-- =============================================================================
-- V2__seed_data.sql — Test Users & 50 Dummy Transactions
-- =============================================================================
-- Flyway Migration: Version 2
--
-- Seeds the database with test data for development and testing.
-- This migration runs AFTER V1__init.sql creates the schema.
--
-- TEST ACCOUNTS:
--   ┌────────────────────────┬───────────────┬─────────┐
--   │ Email                  │ Password      │ Role    │
--   ├────────────────────────┼───────────────┼─────────┤
--   │ admin@zorvyn.com       │ Admin@123     │ ADMIN   │
--   │ analyst@zorvyn.com     │ Analyst@123   │ ANALYST │
--   │ viewer@zorvyn.com      │ Viewer@123    │ VIEWER  │
--   └────────────────────────┴───────────────┴─────────┘
--
-- PASSWORD HASHES:
--   Generated with BCrypt (10 rounds). These are the hashes of the
--   plaintext passwords listed above. You can verify them with:
--     BCryptPasswordEncoder.matches("Admin@123", "$2a$10$...")
--
-- TRANSACTION DATA:
--   50 realistic financial transactions across 10 categories,
--   spanning a 6-month period (Oct 2025 — Mar 2026).
--   Mix of INCOME (15) and EXPENSE (35) to simulate real-world ratios.
-- =============================================================================

-- ─────────────────────────────────────────────────────────────────────────────
-- SEED USERS — One per Role
-- ─────────────────────────────────────────────────────────────────────────────
-- BCrypt hashes of the passwords (10 rounds):
--   Admin@123   → $2a$10$EqKcp1WFKScFBbZ4FMwvRuv3yFDBQiMnJVFKr5dKBSMy9V2CwR22K
--   Analyst@123 → $2a$10$IzFGgIKC2qHQbEpFkN4sruJprXz1NGmW4WaLMTrRIIkOaX1y2.4Lm
--   Viewer@123  → $2a$10$LqGHNFAjMSOUlqxCPMKDn.RfaBhjVjX0M1dbdZH1MlyNi5OC3vJI2
-- ─────────────────────────────────────────────────────────────────────────────

INSERT INTO users (full_name, email, password, role, is_active, created_at)
VALUES
    -- ADMIN: Full access — can create, update, delete, and manage users
    ('System Administrator',
     'admin@zorvyn.com',
     '$2a$10$EqKcp1WFKScFBbZ4FMwvRuv3yFDBQiMnJVFKr5dKBSMy9V2CwR22K',
     'ADMIN', TRUE, NOW()),

    -- ANALYST: Read + Write — can create and update transactions
    ('Financial Analyst',
     'analyst@zorvyn.com',
     '$2a$10$IzFGgIKC2qHQbEpFkN4sruJprXz1NGmW4WaLMTrRIIkOaX1y2.4Lm',
     'ANALYST', TRUE, NOW()),

    -- VIEWER: Read only — can view transactions and dashboard
    ('Report Viewer',
     'viewer@zorvyn.com',
     '$2a$10$LqGHNFAjMSOUlqxCPMKDn.RfaBhjVjX0M1dbdZH1MlyNi5OC3vJI2',
     'VIEWER', TRUE, NOW());

-- ─────────────────────────────────────────────────────────────────────────────
-- SEED TRANSACTIONS — 50 Realistic Financial Records
-- ─────────────────────────────────────────────────────────────────────────────
-- Categories used:
--   INCOME:  Salary, Freelance, Investments, Rental Income, Refunds
--   EXPENSE: Groceries, Utilities, Rent, Transportation, Entertainment,
--            Healthcare, Insurance, Education, Dining Out, Subscriptions
--
-- All transactions are created by user ID 1 (admin) for simplicity.
-- Dates span Oct 2025 — Mar 2026 for realistic date-range filtering tests.
-- ─────────────────────────────────────────────────────────────────────────────

INSERT INTO transactions (amount, type, category, transaction_date, notes, created_by_user_id, last_modified_by_user_id, created_at, is_deleted)
VALUES
    -- ════════════════════════════════════════════════════════════════
    -- INCOME TRANSACTIONS (15)
    -- ════════════════════════════════════════════════════════════════
    -- Monthly salary entries (6 months)
    (8500.0000, 'INCOME', 'Salary', '2025-10-01',
     'October 2025 monthly salary — base compensation', 1, 1, '2025-10-01T09:00:00Z', FALSE),

    (8500.0000, 'INCOME', 'Salary', '2025-11-01',
     'November 2025 monthly salary', 1, 1, '2025-11-01T09:00:00Z', FALSE),

    (8500.0000, 'INCOME', 'Salary', '2025-12-01',
     'December 2025 monthly salary', 1, 1, '2025-12-01T09:00:00Z', FALSE),

    (8500.0000, 'INCOME', 'Salary', '2026-01-01',
     'January 2026 monthly salary', 1, 1, '2026-01-01T09:00:00Z', FALSE),

    (9200.0000, 'INCOME', 'Salary', '2026-02-01',
     'February 2026 salary — includes annual raise adjustment', 1, 1, '2026-02-01T09:00:00Z', FALSE),

    (9200.0000, 'INCOME', 'Salary', '2026-03-01',
     'March 2026 monthly salary', 1, 1, '2026-03-01T09:00:00Z', FALSE),

    -- Freelance income (sporadic)
    (3200.0000, 'INCOME', 'Freelance', '2025-10-15',
     'UI/UX consulting project — Acme Corp dashboard redesign', 1, 1, '2025-10-15T14:00:00Z', FALSE),

    (4750.0000, 'INCOME', 'Freelance', '2025-12-20',
     'Backend API development — SmartRetail inventory system', 1, 1, '2025-12-20T14:00:00Z', FALSE),

    (2100.0000, 'INCOME', 'Freelance', '2026-02-10',
     'Technical writing — API documentation for FinTech startup', 1, 1, '2026-02-10T14:00:00Z', FALSE),

    -- Investment returns
    (1850.0000, 'INCOME', 'Investments', '2025-11-15',
     'Q3 2025 dividend payout — S&P 500 index fund', 1, 1, '2025-11-15T10:00:00Z', FALSE),

    (2340.5000, 'INCOME', 'Investments', '2026-02-15',
     'Q4 2025 dividend payout + capital gains distribution', 1, 1, '2026-02-15T10:00:00Z', FALSE),

    -- Rental income
    (1500.0000, 'INCOME', 'Rental Income', '2025-11-01',
     'Unit 4B monthly rent — residential property', 1, 1, '2025-11-01T12:00:00Z', FALSE),

    (1500.0000, 'INCOME', 'Rental Income', '2026-01-01',
     'Unit 4B monthly rent — Q1 2026', 1, 1, '2026-01-01T12:00:00Z', FALSE),

    -- Refunds
    (249.9900, 'INCOME', 'Refunds', '2025-12-10',
     'Returned defective wireless headphones — Amazon', 1, 1, '2025-12-10T11:00:00Z', FALSE),

    -- HIGH VALUE TRANSACTION — triggers async alert ($12,500 > $10,000 threshold)
    (12500.0000, 'INCOME', 'Investments', '2026-03-20',
     'Stock sale proceeds — NVDA position partial liquidation (HIGH VALUE)', 1, 1, '2026-03-20T10:00:00Z', FALSE),

    -- ════════════════════════════════════════════════════════════════
    -- EXPENSE TRANSACTIONS (35)
    -- ════════════════════════════════════════════════════════════════

    -- Rent (monthly, 6 months)
    (2200.0000, 'EXPENSE', 'Rent', '2025-10-01',
     'October apartment rent — Downtown 2BR', 1, 1, '2025-10-01T08:00:00Z', FALSE),

    (2200.0000, 'EXPENSE', 'Rent', '2025-11-01',
     'November apartment rent', 1, 1, '2025-11-01T08:00:00Z', FALSE),

    (2200.0000, 'EXPENSE', 'Rent', '2025-12-01',
     'December apartment rent', 1, 1, '2025-12-01T08:00:00Z', FALSE),

    (2200.0000, 'EXPENSE', 'Rent', '2026-01-01',
     'January apartment rent', 1, 1, '2026-01-01T08:00:00Z', FALSE),

    (2200.0000, 'EXPENSE', 'Rent', '2026-02-01',
     'February apartment rent', 1, 1, '2026-02-01T08:00:00Z', FALSE),

    (2200.0000, 'EXPENSE', 'Rent', '2026-03-01',
     'March apartment rent', 1, 1, '2026-03-01T08:00:00Z', FALSE),

    -- Groceries (bi-weekly, realistic variation)
    (342.6500, 'EXPENSE', 'Groceries', '2025-10-05',
     'Whole Foods — weekly grocery run', 1, 1, '2025-10-05T16:00:00Z', FALSE),

    (287.3200, 'EXPENSE', 'Groceries', '2025-10-19',
     'Trader Joes — essentials restock', 1, 1, '2025-10-19T15:00:00Z', FALSE),

    (415.8800, 'EXPENSE', 'Groceries', '2025-11-22',
     'Thanksgiving meal prep — Costco bulk purchase', 1, 1, '2025-11-22T14:00:00Z', FALSE),

    (298.4400, 'EXPENSE', 'Groceries', '2025-12-23',
     'Holiday dinner ingredients', 1, 1, '2025-12-23T13:00:00Z', FALSE),

    (356.1200, 'EXPENSE', 'Groceries', '2026-01-10',
     'New year healthy eating start — organic produce', 1, 1, '2026-01-10T16:00:00Z', FALSE),

    (312.7500, 'EXPENSE', 'Groceries', '2026-02-14',
     'Valentines day special dinner ingredients', 1, 1, '2026-02-14T15:00:00Z', FALSE),

    (389.9000, 'EXPENSE', 'Groceries', '2026-03-08',
     'Monthly grocery haul — spring produce', 1, 1, '2026-03-08T16:00:00Z', FALSE),

    -- Utilities
    (185.4300, 'EXPENSE', 'Utilities', '2025-10-15',
     'Electric bill — October (AC usage)', 1, 1, '2025-10-15T09:00:00Z', FALSE),

    (142.7700, 'EXPENSE', 'Utilities', '2025-11-15',
     'Electric + Gas — November (heating season start)', 1, 1, '2025-11-15T09:00:00Z', FALSE),

    (198.5200, 'EXPENSE', 'Utilities', '2025-12-15',
     'Electric + Gas — December (peak heating)', 1, 1, '2025-12-15T09:00:00Z', FALSE),

    (210.0500, 'EXPENSE', 'Utilities', '2026-01-15',
     'Electric + Gas + Water — January utility bundle', 1, 1, '2026-01-15T09:00:00Z', FALSE),

    (165.3300, 'EXPENSE', 'Utilities', '2026-03-15',
     'March utilities — spring rate adjustment', 1, 1, '2026-03-15T09:00:00Z', FALSE),

    -- Transportation
    (65.0000, 'EXPENSE', 'Transportation', '2025-10-20',
     'Monthly transit pass — Metro system', 1, 1, '2025-10-20T07:00:00Z', FALSE),

    (48.5000, 'EXPENSE', 'Transportation', '2025-11-10',
     'Uber rides — client meetings downtown', 1, 1, '2025-11-10T18:00:00Z', FALSE),

    (72.3000, 'EXPENSE', 'Transportation', '2026-01-25',
     'Gas fill-up + parking — weekend road trip', 1, 1, '2026-01-25T11:00:00Z', FALSE),

    (65.0000, 'EXPENSE', 'Transportation', '2026-03-01',
     'March transit pass renewal', 1, 1, '2026-03-01T07:00:00Z', FALSE),

    -- Entertainment
    (89.9700, 'EXPENSE', 'Entertainment', '2025-10-31',
     'Halloween concert tickets — local venue', 1, 1, '2025-10-31T19:00:00Z', FALSE),

    (149.9900, 'EXPENSE', 'Entertainment', '2025-12-25',
     'Christmas movie marathon — streaming + snacks', 1, 1, '2025-12-25T14:00:00Z', FALSE),

    (220.0000, 'EXPENSE', 'Entertainment', '2026-02-14',
     'Valentines day dinner & show', 1, 1, '2026-02-14T19:00:00Z', FALSE),

    -- Healthcare
    (150.0000, 'EXPENSE', 'Healthcare', '2025-11-05',
     'Annual physical exam — copay after insurance', 1, 1, '2025-11-05T10:00:00Z', FALSE),

    (45.0000, 'EXPENSE', 'Healthcare', '2026-01-12',
     'Prescription medication — monthly refill', 1, 1, '2026-01-12T14:00:00Z', FALSE),

    (320.0000, 'EXPENSE', 'Healthcare', '2026-03-22',
     'Dental cleaning + X-rays', 1, 1, '2026-03-22T09:00:00Z', FALSE),

    -- Insurance
    (450.0000, 'EXPENSE', 'Insurance', '2025-10-01',
     'Q4 2025 health insurance premium', 1, 1, '2025-10-01T08:00:00Z', FALSE),

    (450.0000, 'EXPENSE', 'Insurance', '2026-01-01',
     'Q1 2026 health insurance premium', 1, 1, '2026-01-01T08:00:00Z', FALSE),

    -- Education
    (499.0000, 'EXPENSE', 'Education', '2025-11-01',
     'Udemy course bundle — Cloud Architecture + Kubernetes', 1, 1, '2025-11-01T20:00:00Z', FALSE),

    (89.0000, 'EXPENSE', 'Education', '2026-02-01',
     'OReilly Learning subscription — annual renewal', 1, 1, '2026-02-01T20:00:00Z', FALSE),

    -- Dining Out
    (78.5000, 'EXPENSE', 'Dining Out', '2025-10-12',
     'Team dinner — Italian restaurant', 1, 1, '2025-10-12T20:00:00Z', FALSE),

    (125.7500, 'EXPENSE', 'Dining Out', '2025-12-31',
     'New Years Eve dinner — upscale restaurant', 1, 1, '2025-12-31T21:00:00Z', FALSE),

    -- Subscriptions (recurring)
    (15.9900, 'EXPENSE', 'Subscriptions', '2025-10-01',
     'Netflix premium plan — monthly', 1, 1, '2025-10-01T00:00:00Z', FALSE);

-- =============================================================================
-- VERIFICATION QUERIES (for development — not executed by Flyway)
-- =============================================================================
-- After seeding, you can verify the data with these queries:
--
--   SELECT role, COUNT(*) FROM users GROUP BY role;
--   -- Expected: ADMIN=1, ANALYST=1, VIEWER=1
--
--   SELECT type, COUNT(*), SUM(amount) FROM transactions GROUP BY type;
--   -- Expected: INCOME=15 (high sum), EXPENSE=35 (lower sum)
--
--   SELECT category, type, COUNT(*), SUM(amount)
--   FROM transactions GROUP BY category, type ORDER BY SUM(amount) DESC;
--   -- Expected: Salary at top, Subscriptions at bottom
-- =============================================================================
