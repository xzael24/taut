-- ===================================================
-- TAUT — Platform Daur Ulang Digital
-- Version: V1_0_4
-- Description: Production-grade OTP rate limiting with
--              phone_number as PK, advisory locks, UPSERT,
--              and expiration tracking.
-- ===================================================

-- Phone-based OTP rate limits (request + verify, scoped via key prefix)
-- phone_number serves as the natural PK — plain number for requests,
-- "v:" prefix for verify attempts. Uses pg_advisory_xact_lock for
-- serialization and UPSERT (ON CONFLICT) for atomic counter updates.
CREATE TABLE IF NOT EXISTS rate_limits (
    phone_number VARCHAR(20) PRIMARY KEY,
    request_count INTEGER NOT NULL DEFAULT 0,
    window_start  TIMESTAMPTZ NOT NULL,
    expires_at    TIMESTAMPTZ NOT NULL,
    updated_at    TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

COMMENT ON TABLE rate_limits IS
    'OTP rate limiting keyed by phone_number. Scoped via prefix: plain phone = request limit, v: prefix = verify limit. Uses pg_advisory_xact_lock(hashtext(phone_number)) for per-key serialization.';
COMMENT ON COLUMN rate_limits.phone_number IS
    'Primary key / bucket key. Plain phone (08xxx) for OTP request limits; v: prefixed for OTP verify limits.';
COMMENT ON COLUMN rate_limits.request_count IS
    'Number of OTP requests or verify attempts in the current sliding window.';
COMMENT ON COLUMN rate_limits.window_start IS
    'Start timestamp of the current rate limit window.';
COMMENT ON COLUMN rate_limits.expires_at IS
    'Expiration of the current window. Expired rows are reset on the next UPSERT.';
COMMENT ON COLUMN rate_limits.updated_at IS
    'Last modification timestamp. Used for background cleanup of stale rows.';

CREATE INDEX IF NOT EXISTS idx_rate_limits_expires
    ON rate_limits (expires_at);

-- Device-based OTP rate limits (same design, keyed by device_id)
CREATE TABLE IF NOT EXISTS device_rate_limits (
    device_id     VARCHAR(100) PRIMARY KEY,
    request_count INTEGER NOT NULL DEFAULT 0,
    window_start  TIMESTAMPTZ NOT NULL,
    expires_at    TIMESTAMPTZ NOT NULL,
    updated_at    TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

COMMENT ON TABLE device_rate_limits IS
    'OTP request rate limiting per device. Uses pg_advisory_xact_lock(hashtext(device_id)) for per-device serialization.';

CREATE INDEX IF NOT EXISTS idx_device_rate_limits_expires
    ON device_rate_limits (expires_at);
