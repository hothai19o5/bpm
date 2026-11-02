-- Runs only when the PostgreSQL data directory is initialized.
-- CURRENT_USER keeps this valid when POSTGRES_USER is overridden for local dev.
CREATE SCHEMA IF NOT EXISTS flowable AUTHORIZATION CURRENT_USER;
CREATE SCHEMA IF NOT EXISTS business AUTHORIZATION CURRENT_USER;
