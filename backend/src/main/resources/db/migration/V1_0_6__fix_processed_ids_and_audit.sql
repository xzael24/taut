-- ===================================================
-- TAUT — Platform Daur Ulang Digital
-- Version: V1_0_6
-- Description: Fix processed_ids to match Tables.kt and
--              service code expectations. Create audit_logs
--              matching Tables.kt AuditLogs.
-- ===================================================

-- ──── Fix Processed IDs ────
-- The original V1_0_0 created: id, entity_type, processed_at
-- Tables.kt and service code expect: id, idempotency_key, result_type, result_id, created_at
-- Since this is an idempotency dedup table (no critical data), we add missing columns.
ALTER TABLE processed_ids ADD COLUMN IF NOT EXISTS idempotency_key VARCHAR(100);
ALTER TABLE processed_ids ADD COLUMN IF NOT EXISTS result_type VARCHAR(50);
ALTER TABLE processed_ids ADD COLUMN IF NOT EXISTS result_id VARCHAR(100);
ALTER TABLE processed_ids ADD COLUMN IF NOT EXISTS created_at TIMESTAMPTZ;

-- Drop old columns that are no longer used by the code
ALTER TABLE processed_ids DROP COLUMN IF EXISTS entity_type;
ALTER TABLE processed_ids DROP COLUMN IF EXISTS processed_at;

-- Add unique index on idempotency_key for fast dedup lookups
CREATE UNIQUE INDEX IF NOT EXISTS idx_processed_idempotency_key ON processed_ids (idempotency_key) WHERE idempotency_key IS NOT NULL;

-- Set NOT NULL on columns that the code always writes
-- (can only do this after ensuring existing rows are migrated)
-- We use a safe approach: only apply NOT NULL if no NULLs exist
DO $$
BEGIN
    IF EXISTS (SELECT 1 FROM processed_ids WHERE idempotency_key IS NULL LIMIT 1) THEN
        UPDATE processed_ids SET idempotency_key = id::text || '-migrated' WHERE idempotency_key IS NULL;
    END IF;
END $$;

COMMENT ON TABLE processed_ids IS 'Idempotency dedup table. Checked before every mutation.';
COMMENT ON COLUMN processed_ids.idempotency_key IS 'Client-provided idempotency key for deduplication';
COMMENT ON COLUMN processed_ids.result_type IS 'Type of result (transaction, pin_change, void_transaction)';
COMMENT ON COLUMN processed_ids.result_id IS 'ID of the created/modified resource';

-- ──── Create audit_logs table ────
-- Maps to Tables.kt AuditLogs (table: audit_logs)
-- Note: V1_0_0 created `audit_log` (singular) with different partitioning.
-- This table matches the Exposed ORM definition in Tables.kt and
-- matches the INSERT statement used by service code.
CREATE TABLE IF NOT EXISTS audit_logs (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    actor_id        UUID REFERENCES users(id),
    action          VARCHAR(100) NOT NULL,
    resource_type   VARCHAR(50),
    resource_id     VARCHAR(100),
    details         TEXT,
    ip_address      VARCHAR(45),
    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

COMMENT ON TABLE audit_logs IS 'Immutable audit trail for all data access and administrative actions (matching Tables.kt AuditLogs)';
COMMENT ON COLUMN audit_logs.actor_id IS 'User who performed the action (nullable for system actions)';
COMMENT ON COLUMN audit_logs.action IS 'Action performed (e.g., transaction:create, device:register, auth:verify_otp)';
COMMENT ON COLUMN audit_logs.resource_type IS 'Type of resource affected (transaction, device, user, etc.)';
COMMENT ON COLUMN audit_logs.resource_id IS 'ID of the affected resource';
COMMENT ON COLUMN audit_logs.details IS 'Human-readable description of the action';

CREATE INDEX IF NOT EXISTS idx_audit_logs_actor ON audit_logs (actor_id, created_at DESC);
CREATE INDEX IF NOT EXISTS idx_audit_logs_resource ON audit_logs (resource_type, resource_id);
CREATE INDEX IF NOT EXISTS idx_audit_logs_action ON audit_logs (action, created_at DESC);
CREATE INDEX IF NOT EXISTS idx_audit_logs_created ON audit_logs (created_at DESC);
