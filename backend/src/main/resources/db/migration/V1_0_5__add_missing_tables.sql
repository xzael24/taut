-- ===================================================
-- TAUT — Platform Daur Ulang Digital
-- Version: V1_0_5
-- Description: Add missing tables from Tables.kt —
--              otp_sessions, refresh_tokens,
--              catalog_version, sync_logs
-- ===================================================

-- ──── OTP Sessions ────
-- Maps to Tables.kt OtpSessions (table: otp_sessions)
CREATE TABLE IF NOT EXISTS otp_sessions (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    phone_number    VARCHAR(20) NOT NULL,
    otp_hash        VARCHAR(200) NOT NULL,
    device_id       VARCHAR(100),
    bank_sampah_id  UUID,
    expires_at      TIMESTAMPTZ NOT NULL,
    verified        BOOLEAN NOT NULL DEFAULT false,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

COMMENT ON TABLE otp_sessions IS 'OTP verification sessions — in lieu of Redis. Stores BCrypt-hashed OTP codes with TTL.';
COMMENT ON COLUMN otp_sessions.otp_hash IS 'BCrypt hash of the 6-digit OTP code';
COMMENT ON COLUMN otp_sessions.expires_at IS 'OTP expiration timestamp (default 5 minutes from creation)';
COMMENT ON COLUMN otp_sessions.verified IS 'Set to true after successful OTP verification (one-time use)';

CREATE INDEX IF NOT EXISTS idx_otp_phone ON otp_sessions (phone_number, created_at DESC);
CREATE INDEX IF NOT EXISTS idx_otp_expires ON otp_sessions (expires_at) WHERE verified = false;

-- ──── Refresh Tokens ────
-- Maps to Tables.kt RefreshTokens (table: refresh_tokens)
CREATE TABLE IF NOT EXISTS refresh_tokens (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id         UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    token_hash      VARCHAR(200) NOT NULL,
    device_id       VARCHAR(100),
    expires_at      TIMESTAMPTZ NOT NULL,
    is_revoked      BOOLEAN NOT NULL DEFAULT false,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

COMMENT ON TABLE refresh_tokens IS 'Refresh tokens for JWT rotation. Token is stored as BCrypt hash for security.';
COMMENT ON COLUMN refresh_tokens.token_hash IS 'BCrypt hash of the raw refresh token (never store plaintext)';
COMMENT ON COLUMN refresh_tokens.is_revoked IS 'Soft-delete flag for token rotation (family-invalidation on re-use)';

CREATE INDEX IF NOT EXISTS idx_refresh_token_hash ON refresh_tokens (token_hash);
CREATE INDEX IF NOT EXISTS idx_refresh_user ON refresh_tokens (user_id, is_revoked, expires_at DESC);

-- ──── Catalog Version ────
-- Maps to Tables.kt CatalogVersion (table: catalog_version)
CREATE TABLE IF NOT EXISTS catalog_version (
    id              INTEGER PRIMARY KEY,
    version         INTEGER NOT NULL DEFAULT 1,
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

COMMENT ON TABLE catalog_version IS 'Monotonic version counter for catalog/inventory sync. Incremented on every price or category update.';
COMMENT ON COLUMN catalog_version.version IS 'Current catalog version number — used by sync protocol for delta updates';

-- Seed initial catalog version
INSERT INTO catalog_version (id, version, updated_at)
VALUES (1, 1, NOW())
ON CONFLICT (id) DO NOTHING;

-- ──── Sync Logs ────
-- Maps to Tables.kt SyncLogs (table: sync_logs)
-- Note: V1_0_0 created `sync_log` (singular) with different schema.
-- This table matches the Exposed ORM definition in Tables.kt.
CREATE TABLE IF NOT EXISTS sync_logs (
    id                UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    device_id         UUID NOT NULL REFERENCES devices(id) ON DELETE CASCADE,
    sync_status       VARCHAR(30) NOT NULL,
    last_sync_cursor  VARCHAR(100),
    lamport_timestamp BIGINT,
    records_synced    INTEGER NOT NULL DEFAULT 0,
    records_failed    INTEGER NOT NULL DEFAULT 0,
    started_at        TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    completed_at      TIMESTAMPTZ,
    error_message     TEXT,
    created_at        TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

COMMENT ON TABLE sync_logs IS 'Audit trail for every device-server sync session (matching Tables.kt SyncLogs)';
COMMENT ON COLUMN sync_logs.sync_status IS 'Current sync session status: in_progress, completed, partial, failed';
COMMENT ON COLUMN sync_logs.last_sync_cursor IS 'Opaque cursor string for incremental sync continuation';
COMMENT ON COLUMN sync_logs.lamport_timestamp IS 'Lamport logical clock value at end of sync session';

CREATE INDEX IF NOT EXISTS idx_sync_logs_device ON sync_logs (device_id, created_at DESC);
CREATE INDEX IF NOT EXISTS idx_sync_logs_status ON sync_logs (sync_status);

-- ──── Add missing columns to devices table ────
-- The Tables.kt Devices table includes is_wiped and wiped_at columns
ALTER TABLE devices ADD COLUMN IF NOT EXISTS is_wiped BOOLEAN NOT NULL DEFAULT false;
ALTER TABLE devices ADD COLUMN IF NOT EXISTS wiped_at TIMESTAMPTZ;

COMMENT ON COLUMN devices.is_wiped IS 'Remote wipe flag — set on admin-initiated device wipe';
COMMENT ON COLUMN devices.wiped_at IS 'Timestamp of remote wipe execution';
