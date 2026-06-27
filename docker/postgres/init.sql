-- ============================================================
-- init.sql — Runs once when the PostgreSQL container starts
-- for the first time (volume is empty).
-- ============================================================

-- Create the application user (if not already created by POSTGRES_USER)
DO $$
BEGIN
    IF NOT EXISTS (SELECT FROM pg_roles WHERE rolname = 'taut') THEN
        CREATE ROLE taut WITH LOGIN PASSWORD 'taut';
    END IF;
END
$$;

-- Grant privileges
GRANT ALL PRIVILEGES ON DATABASE taut TO taut;

-- Switch to the taut database for schema-level grants
\c taut

GRANT ALL ON SCHEMA public TO taut;
ALTER DEFAULT PRIVILEGES IN SCHEMA public GRANT ALL ON TABLES TO taut;
ALTER DEFAULT PRIVILEGES IN SCHEMA public GRANT ALL ON SEQUENCES TO taut;
ALTER DEFAULT PRIVILEGES IN SCHEMA public GRANT ALL ON FUNCTIONS TO taut;

-- Flyway migration table will be created automatically by the app.
-- This init script only ensures the user and privileges are correct.
