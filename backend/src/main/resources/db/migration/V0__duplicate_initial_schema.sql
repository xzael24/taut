-- ===================================================
-- TAUT — Platform Daur Ulang Digital
-- Version: V1
-- Description: Initial schema — all tables from
--              Tables.kt (Exposed ORM definitions),
--              enums, indexes, partitions, seed data.
-- ===================================================

-- ──── Extensions ────

CREATE EXTENSION IF NOT EXISTS pgcrypto;

-- ──── Enums ────

CREATE TYPE user_role AS ENUM (
    'operator',      -- Bank sampah operator (primary user)
    'customer',      -- Household nasabah (app+phone, minimal data)
    'dlh_staff',     -- Dinas Lingkungan Hidup (read-only access)
    'admin',         -- TAUT platform admin
    'superadmin'     -- Full system access (limited, audited)
);

CREATE TYPE transaction_type AS ENUM (
    'deposit',        -- Customer deposits waste
    'redemption'      -- Customer redeems points (Fase 1+)
);

CREATE TYPE transaction_status AS ENUM (
    'pending_sync',   -- Created offline, not yet synced to server
    'syncing',        -- Currently being synced
    'synced',         -- Successfully synced to server
    'confirmed',      -- Verified and reconciled by server
    'failed',         -- Sync failed (retryable)
    'failed_manual',  -- Requires human intervention
    'voided'          -- Voided by admin (compensating entry created)
);

CREATE TYPE sync_status AS ENUM ('in_progress', 'completed', 'partial', 'failed');

CREATE TYPE sms_status AS ENUM (
    'pending', 'sending', 'sent', 'failed', 'bounced', 'cancelled'
);

CREATE TYPE account_type AS ENUM (
    'asset', 'liability', 'equity', 'revenue', 'expense'
);

CREATE TYPE entry_type AS ENUM ('debit', 'credit');

CREATE TYPE audit_action AS ENUM (
    'user_export', 'user_forget', 'admin_user_view', 'admin_user_edit',
    'device_register', 'device_wipe', 'pin_change', 'price_update',
    'fraud_flag', 'fraud_review'
);

-- ──── Reference Tables ────

CREATE TABLE provinsis (
    id              INTEGER PRIMARY KEY GENERATED ALWAYS AS IDENTITY,
    name_id         VARCHAR(255) NOT NULL,
    iso_code        VARCHAR(5)                      -- e.g., ID-JB for Jawa Barat
);

CREATE TABLE kotas (
    id              INTEGER PRIMARY KEY GENERATED ALWAYS AS IDENTITY,
    provinsi_id     INTEGER NOT NULL REFERENCES provinsis(id),
    name_id         VARCHAR(255) NOT NULL
);

CREATE TABLE kecamatans (
    id              INTEGER PRIMARY KEY GENERATED ALWAYS AS IDENTITY,
    kota_id         INTEGER NOT NULL REFERENCES kotas(id),
    name_id         VARCHAR(255) NOT NULL,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE TABLE villages (
    id              INTEGER PRIMARY KEY GENERATED ALWAYS AS IDENTITY,
    kecamatan_id    INTEGER NOT NULL REFERENCES kecamatans(id),
    name_id         VARCHAR(255) NOT NULL,
    postal_code     VARCHAR(10),
    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

COMMENT ON TABLE villages IS 'Administrative villages (desa/kelurahan) — from BPS standard dataset. NOT GPS coordinates.';

-- ──── Core Tables ────

-- Waste Banks (maps to WasteBanks table object in Tables.kt)
CREATE TABLE waste_banks (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    code            VARCHAR(20) NOT NULL UNIQUE,  -- Format: BSA-XXXX
    name            VARCHAR(200) NOT NULL,
    phone           VARCHAR(20) NOT NULL,
    address         VARCHAR(500),
    village_id      INTEGER NOT NULL REFERENCES villages(id),
    photo_url       VARCHAR(500),
    device_pub_key  TEXT,
    is_active       BOOLEAN NOT NULL DEFAULT true,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

COMMENT ON TABLE waste_banks IS 'Registered bank sampah locations';
COMMENT ON COLUMN waste_banks.code IS 'Human-readable unique code: BSA-XXXX where XXXX is sequential';
COMMENT ON COLUMN waste_banks.device_pub_key IS 'Ed25519 or ECDSA public key for device-level HMAC-SHA256 signature verification';

-- Users (maps to Users table object in Tables.kt)
CREATE TABLE users (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    phone_number    VARCHAR(20) NOT NULL UNIQUE,
    role            VARCHAR(20) NOT NULL DEFAULT 'CUSTOMER',
    name            VARCHAR(200),
    village_id      INTEGER REFERENCES villages(id),
    kyc_status      VARCHAR(20) NOT NULL DEFAULT 'unverified',
    pin_hash        VARCHAR(200),
    pin_salt        VARCHAR(100),
    failed_pin_attempts INTEGER NOT NULL DEFAULT 0,
    is_active       BOOLEAN NOT NULL DEFAULT true,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

COMMENT ON TABLE users IS 'All platform users: operators, customers, DLH, admins';
COMMENT ON COLUMN users.failed_pin_attempts IS 'Security: triggers device auto-wipe at 10 failed attempts';

-- Operator Profiles (maps to OperatorProfiles table object in Tables.kt)
CREATE TABLE operator_profiles (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    bank_sampah_id  UUID NOT NULL REFERENCES waste_banks(id) ON DELETE CASCADE,
    user_id         UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    pin_hash        VARCHAR(200),
    is_primary      BOOLEAN NOT NULL DEFAULT false,
    is_active       BOOLEAN NOT NULL DEFAULT true,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    UNIQUE(bank_sampah_id, user_id)
);

COMMENT ON TABLE operator_profiles IS 'Maps operators to bank sampah with per-profile PIN for kiosk mode';

-- Waste Categories (maps to WasteCategories table object in Tables.kt)
CREATE TABLE waste_categories (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    code            VARCHAR(20) NOT NULL UNIQUE,
    name_id         VARCHAR(200) NOT NULL,
    name_en         VARCHAR(200),
    category_group  VARCHAR(50) NOT NULL,
    unit_type       VARCHAR(10) NOT NULL DEFAULT 'kg',
    unit_price      BIGINT NOT NULL DEFAULT 0,
    photo_url       VARCHAR(500),
    sort_order      INTEGER NOT NULL DEFAULT 0,
    is_active       BOOLEAN NOT NULL DEFAULT true,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

COMMENT ON TABLE waste_categories IS 'Waste types with real photos for operator selection';
COMMENT ON COLUMN waste_categories.unit_price IS 'Default static price; overridden by price_references if active';

-- Price References (maps to PriceReferences table object in Tables.kt)
CREATE TABLE price_references (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    category_id     UUID NOT NULL REFERENCES waste_categories(id),
    region_id       INTEGER NOT NULL,
    region_type     VARCHAR(20) NOT NULL DEFAULT 'national',
    price_source    VARCHAR(30) NOT NULL,
    price_per_unit  BIGINT NOT NULL,
    effective_from  TIMESTAMPTZ NOT NULL,
    effective_to    TIMESTAMPTZ,
    version         INTEGER NOT NULL,
    is_current      BOOLEAN NOT NULL DEFAULT true,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

COMMENT ON TABLE price_references IS 'Historical price reference per category per region. Immutable — INSERT only.';

-- ──── Transactions ────

-- Transactions (maps to Transactions table object in Tables.kt)
CREATE TABLE transactions (
    id                  UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    bank_sampah_id      UUID NOT NULL REFERENCES waste_banks(id),
    operator_id         UUID NOT NULL REFERENCES users(id),
    customer_id         UUID NOT NULL REFERENCES users(id),
    transaction_type    VARCHAR(20) NOT NULL DEFAULT 'DEPOSIT',
    status              VARCHAR(30) NOT NULL DEFAULT 'PENDING_SYNC',
    total_weight        BIGINT NOT NULL,
    total_value         BIGINT NOT NULL,
    device_timestamp    TIMESTAMPTZ,
    server_timestamp    TIMESTAMPTZ,
    sync_id             VARCHAR(100),
    is_offline_created  BOOLEAN NOT NULL DEFAULT true,
    lamport_timestamp   BIGINT,
    hmac_signature      VARCHAR(256),
    price_snapshot      TEXT,
    weight_photo_url    VARCHAR(500),
    fraud_flag          BOOLEAN NOT NULL DEFAULT false,
    fraud_reason        VARCHAR(500),
    sms_sent            BOOLEAN NOT NULL DEFAULT false,
    sms_sent_at         TIMESTAMPTZ,
    voided_by           UUID REFERENCES users(id),
    void_reason         VARCHAR(500),
    voided_at           TIMESTAMPTZ,
    created_at          TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at          TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

-- TimescaleDB hypertable (uncomment when TimescaleDB is available)
-- SELECT create_hypertable('transactions', 'created_at', chunk_time_interval => INTERVAL '1 day');

COMMENT ON TABLE transactions IS 'Waste deposit transactions — the core business record';
COMMENT ON COLUMN transactions.total_weight IS 'Total weight in grams (integer)';
COMMENT ON COLUMN transactions.total_value IS 'Total monetary value in satuan rupiah (cents). INTEGER. NO FLOATS.';
COMMENT ON COLUMN transactions.price_snapshot IS 'JSON snapshot of waste_categories.unit_price at transaction time';
COMMENT ON COLUMN transactions.hmac_signature IS 'HMAC-SHA256 of transaction fields signed by device private key';

-- Transaction Items (maps to TransactionItems table object in Tables.kt)
CREATE TABLE transaction_items (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    transaction_id  UUID NOT NULL REFERENCES transactions(id) ON DELETE CASCADE,
    category_id     UUID NOT NULL REFERENCES waste_categories(id),
    weight          BIGINT NOT NULL,
    price_per_unit  BIGINT NOT NULL,
    total_value     BIGINT NOT NULL,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

COMMENT ON TABLE transaction_items IS 'Line items within a transaction — one per waste category deposited';
COMMENT ON COLUMN transaction_items.price_per_unit IS 'LOCKED: the price_per_unit at time of transaction, NOT the current price';

-- ──── Financial Tables ────

-- Ledger Accounts (maps to LedgerAccounts table object in Tables.kt)
CREATE TABLE ledger_accounts (
    id                  UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    account_code        VARCHAR(20) NOT NULL UNIQUE,
    name_id             VARCHAR(200) NOT NULL,
    account_type        VARCHAR(20) NOT NULL,
    normal_balance      VARCHAR(10) NOT NULL CHECK (normal_balance IN ('debit', 'credit')),
    is_active           BOOLEAN NOT NULL DEFAULT true,
    created_at          TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

COMMENT ON TABLE ledger_accounts IS 'Chart of accounts for double-entry bookkeeping';

-- Ledger Entries (maps to LedgerEntries table object in Tables.kt)
CREATE TABLE ledger_entries (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    account_id      UUID NOT NULL REFERENCES ledger_accounts(id),
    entry_type      VARCHAR(10) NOT NULL,   -- debit, credit
    amount          BIGINT NOT NULL CHECK (amount > 0),
    balance_after   BIGINT NOT NULL,
    reference_type  VARCHAR(50) NOT NULL,
    reference_id    UUID NOT NULL,
    reason_code     VARCHAR(50) NOT NULL,
    actor_id        UUID REFERENCES users(id),
    description     VARCHAR(500),
    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW()
) PARTITION BY RANGE (created_at);

COMMENT ON TABLE ledger_entries IS 'Immutable double-entry bookkeeping. Every financial event creates ≥2 rows. NO UPDATES, only INSERT.';

-- Monthly partitions for 2026 (Q3 + 3 months premake)
CREATE TABLE ledger_entries_2026_07 PARTITION OF ledger_entries
    FOR VALUES FROM ('2026-07-01') TO ('2026-08-01');
CREATE TABLE ledger_entries_2026_08 PARTITION OF ledger_entries
    FOR VALUES FROM ('2026-08-01') TO ('2026-09-01');
CREATE TABLE ledger_entries_2026_09 PARTITION OF ledger_entries
    FOR VALUES FROM ('2026-09-01') TO ('2026-10-01');
CREATE TABLE ledger_entries_2026_10 PARTITION OF ledger_entries
    FOR VALUES FROM ('2026-10-01') TO ('2026-11-01');
CREATE TABLE ledger_entries_2026_11 PARTITION OF ledger_entries
    FOR VALUES FROM ('2026-11-01') TO ('2026-12-01');
CREATE TABLE ledger_entries_2026_12 PARTITION OF ledger_entries
    FOR VALUES FROM ('2026-12-01') TO ('2027-01-01');

-- ──── Devices & Sync ────

-- Devices (maps to Devices table object in Tables.kt)
CREATE TABLE devices (
    id                  UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    bank_sampah_id      UUID NOT NULL REFERENCES waste_banks(id),
    device_name         VARCHAR(200),
    device_phone_number VARCHAR(20),
    device_pub_key      TEXT NOT NULL,
    device_fingerprint  VARCHAR(100),
    registered_at       TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    last_seen_at        TIMESTAMPTZ,
    is_active           BOOLEAN NOT NULL DEFAULT true,
    app_version         VARCHAR(30),
    is_wiped            BOOLEAN NOT NULL DEFAULT false,
    wiped_at            TIMESTAMPTZ,
    created_at          TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at          TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

COMMENT ON TABLE devices IS 'Registered Android devices per bank sampah';

-- Sync Logs (maps to SyncLogs table object in Tables.kt)
CREATE TABLE sync_logs (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    device_id       UUID NOT NULL REFERENCES devices(id),
    sync_status     VARCHAR(30) NOT NULL,
    last_sync_cursor VARCHAR(100),
    lamport_timestamp BIGINT,
    records_synced  INTEGER NOT NULL DEFAULT 0,
    records_failed  INTEGER NOT NULL DEFAULT 0,
    started_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    completed_at    TIMESTAMPTZ,
    error_message   TEXT,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

COMMENT ON TABLE sync_logs IS 'Audit trail for every device-server sync session';

-- Processed IDs (maps to ProcessedIds table object in Tables.kt)
CREATE TABLE processed_ids (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    idempotency_key VARCHAR(100) NOT NULL UNIQUE,
    result_type     VARCHAR(50) NOT NULL,
    result_id       VARCHAR(100) NOT NULL,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

COMMENT ON TABLE processed_ids IS 'Idempotency dedup table. Checked before every mutation. Records retained 90 days.';

-- ──── SMS ────

-- SMS Queue (maps to SmsQueue table object in Tables.kt)
CREATE TABLE sms_queue (
    id                  UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    transaction_id      UUID REFERENCES transactions(id),
    phone_to            VARCHAR(20) NOT NULL,
    message             TEXT NOT NULL,
    sms_status          VARCHAR(20) NOT NULL DEFAULT 'pending',
    provider            VARCHAR(30),
    provider_message_id VARCHAR(100),
    cost                BIGINT,
    retry_count         INTEGER NOT NULL DEFAULT 0,
    max_retries         INTEGER NOT NULL DEFAULT 3,
    last_error          TEXT,
    sent_at             TIMESTAMPTZ,
    created_at          TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

COMMENT ON TABLE sms_queue IS 'Queue of SMS messages for transaction receipts and OTP delivery';

-- ──── Compliance Tables ────

-- User Consent Log (maps to UserConsentLog table object in Tables.kt)
CREATE TABLE user_consent_log (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id         UUID NOT NULL REFERENCES users(id),
    consent_type    VARCHAR(50) NOT NULL,
    consent_version VARCHAR(20) NOT NULL,
    granted         BOOLEAN NOT NULL,
    ip_address      INET,
    device_id_sql   VARCHAR(100),
    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

COMMENT ON TABLE user_consent_log IS 'UU PDP Pasal 20 compliance: explicit consent tracking';

-- Audit Log (maps to AuditLogs table object in Tables.kt)
CREATE TABLE audit_logs (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    actor_id        UUID REFERENCES users(id),
    action          VARCHAR(100) NOT NULL,
    resource_type   VARCHAR(50),
    resource_id     VARCHAR(100),
    details         TEXT,
    ip_address      INET,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW()
) PARTITION BY RANGE (created_at);

COMMENT ON TABLE audit_logs IS 'Immutable audit trail for all data access and administrative actions';

-- Audit log partitions (monthly)
CREATE TABLE audit_logs_2026_07 PARTITION OF audit_logs
    FOR VALUES FROM ('2026-07-01') TO ('2026-08-01');
CREATE TABLE audit_logs_2026_08 PARTITION OF audit_logs
    FOR VALUES FROM ('2026-08-01') TO ('2026-09-01');
CREATE TABLE audit_logs_2026_09 PARTITION OF audit_logs
    FOR VALUES FROM ('2026-09-01') TO ('2026-10-01');
CREATE TABLE audit_logs_2026_10 PARTITION OF audit_logs
    FOR VALUES FROM ('2026-10-01') TO ('2026-11-01');
CREATE TABLE audit_logs_2026_11 PARTITION OF audit_logs
    FOR VALUES FROM ('2026-11-01') TO ('2026-12-01');
CREATE TABLE audit_logs_2026_12 PARTITION OF audit_logs
    FOR VALUES FROM ('2026-12-01') TO ('2027-01-01');

-- ──── OTP & Rate Limiting ────

-- OTP Sessions (maps to OtpSessions table object in Tables.kt)
CREATE TABLE otp_sessions (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    phone_number    VARCHAR(20) NOT NULL,
    otp_hash        VARCHAR(200) NOT NULL,
    device_id       VARCHAR(100),
    bank_sampah_id  UUID REFERENCES waste_banks(id),
    expires_at      TIMESTAMPTZ NOT NULL,
    verified        BOOLEAN NOT NULL DEFAULT false,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

COMMENT ON TABLE otp_sessions IS 'OTP verification sessions stored in PostgreSQL (alternative to Redis)';

-- Rate Limits (maps to RateLimits table object in Tables.kt)
CREATE TABLE rate_limits (
    phone_number    VARCHAR(20) PRIMARY KEY,
    request_count   INTEGER NOT NULL DEFAULT 0,
    window_start    TIMESTAMPTZ NOT NULL,
    expires_at      TIMESTAMPTZ NOT NULL,
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

COMMENT ON TABLE rate_limits IS
    'OTP rate limiting keyed by phone_number. Scoped via prefix: plain phone = request limit, v: prefix = verify limit.';

CREATE INDEX idx_rate_limits_expires ON rate_limits (expires_at);

-- Device Rate Limits (maps to DeviceRateLimits table object in Tables.kt)
CREATE TABLE device_rate_limits (
    device_id       VARCHAR(100) PRIMARY KEY,
    request_count   INTEGER NOT NULL DEFAULT 0,
    window_start    TIMESTAMPTZ NOT NULL,
    expires_at      TIMESTAMPTZ NOT NULL,
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

COMMENT ON TABLE device_rate_limits IS
    'OTP request rate limiting per device.';

CREATE INDEX idx_device_rate_limits_expires ON device_rate_limits (expires_at);

-- ──── Refresh Tokens ────

-- Refresh Tokens (maps to RefreshTokens table object in Tables.kt)
CREATE TABLE refresh_tokens (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id         UUID NOT NULL REFERENCES users(id),
    token_hash      VARCHAR(200) NOT NULL,
    device_id       VARCHAR(100),
    expires_at      TIMESTAMPTZ NOT NULL,
    is_revoked      BOOLEAN NOT NULL DEFAULT false,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

COMMENT ON TABLE refresh_tokens IS 'JWT refresh tokens with device binding and revocation support';

CREATE INDEX idx_refresh_tokens_user ON refresh_tokens (user_id);
CREATE INDEX idx_refresh_tokens_expires ON refresh_tokens (expires_at) WHERE is_revoked = false;

-- ──── QR Codes (Fase 2 — structure only) ────

CREATE TABLE qr_codes (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    transaction_id  UUID NOT NULL REFERENCES transactions(id),
    qr_content      JSONB NOT NULL,
    qr_version      INTEGER NOT NULL DEFAULT 1,
    printed_at      TIMESTAMPTZ,
    verified_at     TIMESTAMPTZ,
    expires_at      TIMESTAMPTZ,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

COMMENT ON TABLE qr_codes IS 'QR codes for transaction verification (Fase 2+).';

-- ──── Catalog Version ────

CREATE TABLE catalog_version (
    id              INTEGER PRIMARY KEY,
    version         INTEGER NOT NULL DEFAULT 1,
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

COMMENT ON TABLE catalog_version IS 'Catalog version tracker for cache invalidation and sync';

-- ──── Indexes ────

-- Waste banks
CREATE INDEX idx_waste_banks_code ON waste_banks (code);

-- Users
CREATE INDEX idx_users_phone ON users (phone_number);
CREATE INDEX idx_users_role ON users (role);
CREATE INDEX idx_active_operators ON users (phone_number) WHERE role = 'operator';

-- Operator profiles
CREATE INDEX idx_op_profile_bank_user ON operator_profiles (bank_sampah_id, user_id);

-- Transactions
CREATE INDEX idx_tx_bank_status ON transactions (bank_sampah_id, status);
CREATE INDEX idx_tx_customer ON transactions (customer_id, created_at DESC);
CREATE INDEX idx_tx_device_ts ON transactions (device_timestamp);
CREATE INDEX idx_tx_sync_batch ON transactions (sync_id);
CREATE INDEX idx_tx_lamport ON transactions (lamport_timestamp);
CREATE INDEX idx_tx_operator_dashboard ON transactions (bank_sampah_id, created_at DESC)
    INCLUDE (total_weight, total_value, status, customer_id)
    WHERE status IN ('synced', 'confirmed');
CREATE INDEX idx_tx_pending_sync ON transactions (bank_sampah_id, device_timestamp)
    WHERE status = 'pending_sync'
    INCLUDE (id, total_weight, total_value, customer_id);
CREATE INDEX idx_tx_recent ON transactions (bank_sampah_id, created_at DESC) WHERE status = 'confirmed';

-- Transaction items
CREATE INDEX idx_tx_items_tx ON transaction_items (transaction_id);
CREATE INDEX idx_tx_items_category ON transaction_items (category_id, created_at);

-- Price references
CREATE INDEX idx_price_current ON price_references (category_id, region_id) WHERE is_current = true;
CREATE INDEX idx_price_effective ON price_references (category_id, effective_from, effective_to);
CREATE INDEX idx_price_history_lookup ON price_references (category_id, effective_from DESC);

-- Ledger
CREATE INDEX idx_ledger_account ON ledger_entries (account_id, created_at DESC);
CREATE INDEX idx_ledger_reference ON ledger_entries (reference_type, reference_id);

-- SMS
CREATE INDEX idx_sms_status ON sms_queue (sms_status, retry_count);

-- Sync logs
CREATE INDEX idx_sync_device ON sync_logs (device_id, created_at DESC);

-- Processed IDs
CREATE INDEX idx_processed_ids_lookup ON processed_ids (idempotency_key);

-- Audit logs
CREATE INDEX idx_audit_actor ON audit_logs (actor_id, created_at DESC);
CREATE INDEX idx_audit_resource ON audit_logs (resource_type, resource_id);

-- Consent log
CREATE INDEX idx_consent_user_type ON user_consent_log (user_id, consent_type, created_at DESC);

-- OTP sessions
CREATE INDEX idx_otp_phone ON otp_sessions (phone_number, expires_at DESC);
CREATE INDEX idx_otp_expires ON otp_sessions (expires_at) WHERE verified = false;

-- ──── Materialized Views ────

CREATE MATERIALIZED VIEW mv_daily_rollup_by_bank AS
SELECT
    date_trunc('day', t.created_at) AS day,
    t.bank_sampah_id,
    ti.category_id,
    COUNT(DISTINCT t.id) AS transaction_count,
    COUNT(DISTINCT t.customer_id) AS unique_customers,
    SUM(ti.weight) AS total_weight_grams,
    SUM(ti.total_value) AS total_value_satuan
FROM transactions t
JOIN transaction_items ti ON ti.transaction_id = t.id
WHERE t.status IN ('synced', 'confirmed')
GROUP BY 1, 2, 3
ORDER BY 1 DESC, 2, 3;

CREATE UNIQUE INDEX idx_mv_daily_bank ON mv_daily_rollup_by_bank (day, bank_sampah_id, category_id);

CREATE MATERIALIZED VIEW mv_weekly_rollup_by_kecamatan AS
SELECT
    date_trunc('week', t.created_at) AS week,
    v.kecamatan_id,
    ti.category_id,
    SUM(ti.weight) AS total_weight_grams,
    SUM(ti.total_value) AS total_value_satuan,
    COUNT(DISTINCT t.bank_sampah_id) AS active_banks
FROM transactions t
JOIN transaction_items ti ON ti.transaction_id = t.id
JOIN waste_banks wb ON wb.id = t.bank_sampah_id
JOIN villages v ON v.id = wb.village_id
WHERE t.status IN ('synced', 'confirmed')
GROUP BY 1, 2, 3
ORDER BY 1 DESC, 2, 3;

CREATE UNIQUE INDEX idx_mv_weekly_kec ON mv_weekly_rollup_by_kecamatan (week, kecamatan_id, category_id);

-- Fraud anomaly detection materialized view
CREATE MATERIALIZED VIEW mv_daily_fraud_anomaly AS
SELECT
    date_trunc('day', t.created_at) AS day,
    t.bank_sampah_id,
    t.operator_id,
    COUNT(t.id) AS tx_count,
    AVG(t.total_weight) AS avg_weight_grams,
    AVG(t.total_value) AS avg_value_satuan,
    STDDEV(t.total_weight) AS weight_stddev
FROM transactions t
WHERE t.status IN ('synced', 'confirmed')
  AND t.created_at > NOW() - INTERVAL '30 days'
GROUP BY 1, 2, 3
HAVING AVG(t.total_value) > (
    SELECT AVG(total_value) * 3 FROM transactions
    WHERE status IN ('synced', 'confirmed')
      AND created_at > NOW() - INTERVAL '30 days'
);

CREATE INDEX IF NOT EXISTS idx_mv_fraud_anomaly_day
    ON mv_daily_fraud_anomaly (day, bank_sampah_id);

-- ===================================================
-- SEED DATA
-- ===================================================

-- ──── Provinces ────
-- Indonesia has 38 provinces as of 2026
INSERT INTO provinsis (id, name_id, iso_code) VALUES
    (1, 'Aceh', 'ID-AC'),
    (2, 'Sumatera Utara', 'ID-SU'),
    (3, 'Sumatera Barat', 'ID-SB'),
    (4, 'Riau', 'ID-RI'),
    (5, 'Jambi', 'ID-JA'),
    (6, 'Sumatera Selatan', 'ID-SS'),
    (7, 'Bengkulu', 'ID-BE'),
    (8, 'Lampung', 'ID-LA'),
    (9, 'Kepulauan Bangka Belitung', 'ID-BB'),
    (10, 'Kepulauan Riau', 'ID-KR'),
    (11, 'DKI Jakarta', 'ID-JK'),
    (12, 'Jawa Barat', 'ID-JB'),
    (13, 'Jawa Tengah', 'ID-JT'),
    (14, 'DI Yogyakarta', 'ID-YO'),
    (15, 'Jawa Timur', 'ID-JI'),
    (16, 'Banten', 'ID-BT'),
    (17, 'Bali', 'ID-BA'),
    (18, 'Nusa Tenggara Barat', 'ID-NB'),
    (19, 'Nusa Tenggara Timur', 'ID-NT'),
    (20, 'Kalimantan Barat', 'ID-KB'),
    (21, 'Kalimantan Tengah', 'ID-KT'),
    (22, 'Kalimantan Selatan', 'ID-KS'),
    (23, 'Kalimantan Timur', 'ID-KI'),
    (24, 'Kalimantan Utara', 'ID-KU'),
    (25, 'Sulawesi Utara', 'ID-SA'),
    (26, 'Sulawesi Tengah', 'ID-ST'),
    (27, 'Sulawesi Selatan', 'ID-SN'),
    (28, 'Sulawesi Tenggara', 'ID-SG'),
    (29, 'Gorontalo', 'ID-GO'),
    (30, 'Sulawesi Barat', 'ID-SR'),
    (31, 'Maluku', 'ID-MA'),
    (32, 'Maluku Utara', 'ID-MU'),
    (33, 'Papua', 'ID-PA'),
    (34, 'Papua Barat', 'ID-PB'),
    (35, 'Papua Selatan', 'ID-PS'),
    (36, 'Papua Tengah', 'ID-PT'),
    (37, 'Papua Pegunungan', 'ID-PE'),
    (38, 'Papua Barat Daya', 'ID-PD');

-- ──── Waste Categories ────
-- 20 core categories with default prices (in satuan rupiah per kg)
INSERT INTO waste_categories (id, code, name_id, name_en, category_group, unit_type, unit_price, sort_order) VALUES
    (gen_random_uuid(), 'KRD-01', 'Kardus/Duplex', 'Cardboard/Duplex', 'kardus', 'kg', 2500, 1),
    (gen_random_uuid(), 'KRD-02', 'Kardus Campur', 'Mixed Cardboard', 'kardus', 'kg', 1500, 2),
    (gen_random_uuid(), 'KRT-01', 'Kertas Putih', 'White Paper', 'kertas', 'kg', 2000, 3),
    (gen_random_uuid(), 'KRT-02', 'Kertas Campur', 'Mixed Paper', 'kertas', 'kg', 1000, 4),
    (gen_random_uuid(), 'KRT-03', 'Koran/Majalah', 'Newspaper/Magazine', 'kertas', 'kg', 1200, 5),
    (gen_random_uuid(), 'PLT-01', 'Botol PET', 'PET Bottles', 'plastik', 'kg', 4000, 6),
    (gen_random_uuid(), 'PLT-02', 'Plastik HDPE', 'HDPE Plastic', 'plastik', 'kg', 3500, 7),
    (gen_random_uuid(), 'PLT-03', 'Plastik Campur', 'Mixed Plastic', 'plastik', 'kg', 1500, 8),
    (gen_random_uuid(), 'PLT-04', 'Kresek/Bakul', 'Plastic Bags', 'plastik', 'kg', 500, 9),
    (gen_random_uuid(), 'GLS-01', 'Botol Kaca', 'Glass Bottles', 'kaca', 'kg', 800, 10),
    (gen_random_uuid(), 'GLS-02', 'Kaca Campur', 'Mixed Glass', 'kaca', 'kg', 400, 11),
    (gen_random_uuid(), 'LGM-01', 'Aluminium', 'Aluminum', 'logam', 'kg', 12000, 12),
    (gen_random_uuid(), 'LGM-02', 'Besi', 'Iron', 'logam', 'kg', 3000, 13),
    (gen_random_uuid(), 'LGM-03', 'Tembaga', 'Copper', 'logam', 'kg', 55000, 14),
    (gen_random_uuid(), 'LGM-04', 'Kuningan', 'Brass', 'logam', 'kg', 35000, 15),
    (gen_random_uuid(), 'LGM-05', 'Kabel', 'Cable', 'logam', 'kg', 15000, 16),
    (gen_random_uuid(), 'ELK-01', 'Elektronik Kecil', 'Small Electronics', 'elektronik', 'kg', 5000, 17),
    (gen_random_uuid(), 'ELK-02', 'Baterai', 'Batteries', 'elektronik', 'kg', 2000, 18),
    (gen_random_uuid(), 'LNY-01', 'Minyak Jelantah', 'Used Cooking Oil', 'lainnya', 'liter', 6000, 19),
    (gen_random_uuid(), 'LNY-02', 'Styrofoam', 'Styrofoam', 'lainnya', 'kg', 300, 20);

-- ──── Ledger Accounts ────

INSERT INTO ledger_accounts (id, account_code, name_id, account_type, normal_balance) VALUES
    (gen_random_uuid(), '1000', 'Kas — Rekening Operasional TAUT', 'asset', 'debit'),
    (gen_random_uuid(), '2000', 'Liabilitas — Poin Nasabah', 'liability', 'credit'),
    (gen_random_uuid(), '2100', 'Liabilitas — Payout Tertunda', 'liability', 'credit'),
    (gen_random_uuid(), '3000', 'Ekuitas — Laba Ditahan', 'equity', 'credit'),
    (gen_random_uuid(), '4000', 'Pendapatan — Komisi', 'revenue', 'credit'),
    (gen_random_uuid(), '4100', 'Pendapatan — Data-as-Service', 'revenue', 'credit'),
    (gen_random_uuid(), '5000', 'Beban — SMS', 'expense', 'debit'),
    (gen_random_uuid(), '5100', 'Beban — Redemption Poin', 'expense', 'debit'),
    (gen_random_uuid(), '5200', 'Beban — Operasional Platform', 'expense', 'debit');
