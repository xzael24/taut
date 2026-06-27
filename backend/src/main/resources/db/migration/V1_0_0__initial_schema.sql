-- ===================================================
-- TAUT — Platform Daur Ulang Digital
-- Version: V1_0_0
-- Description: Initial schema — core entities, financial tables,
--              sync infrastructure, compliance tables
-- ===================================================

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

CREATE TABLE waste_banks (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    code            VARCHAR(20) NOT NULL UNIQUE,  -- Format: BSA-XXXX
    name            VARCHAR(255) NOT NULL,
    phone           VARCHAR(20) NOT NULL,
    address         TEXT,
    village_id      INTEGER NOT NULL REFERENCES villages(id),
    photo_url       TEXT,
    device_pub_key  TEXT,
    is_active       BOOLEAN NOT NULL DEFAULT true,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

COMMENT ON TABLE waste_banks IS 'Registered bank sampah locations';
COMMENT ON COLUMN waste_banks.code IS 'Human-readable unique code: BSA-XXXX where XXXX is sequential';
COMMENT ON COLUMN waste_banks.device_pub_key IS 'Ed25519 or ECDSA public key for device-level HMAC-SHA256 signature verification';

CREATE TABLE users (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    phone_number    VARCHAR(20) NOT NULL UNIQUE,
    role            user_role NOT NULL DEFAULT 'customer',
    name            VARCHAR(255),
    village_id      INTEGER REFERENCES villages(id),
    kyc_status      VARCHAR(20) NOT NULL DEFAULT 'unverified',
    pin_hash        VARCHAR(255),
    pin_salt        VARCHAR(64),
    failed_pin_attempts INTEGER NOT NULL DEFAULT 0,
    is_active       BOOLEAN NOT NULL DEFAULT true,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

COMMENT ON TABLE users IS 'All platform users: operators, customers, DLH, admins';
COMMENT ON COLUMN users.failed_pin_attempts IS 'Security: triggers device auto-wipe at 10 failed attempts';

CREATE TABLE operator_profiles (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    bank_sampah_id  UUID NOT NULL REFERENCES waste_banks(id) ON DELETE CASCADE,
    user_id         UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    pin_hash        VARCHAR(255) NOT NULL,
    is_primary      BOOLEAN NOT NULL DEFAULT false,
    is_active       BOOLEAN NOT NULL DEFAULT true,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    UNIQUE(bank_sampah_id, user_id)
);

COMMENT ON TABLE operator_profiles IS 'Maps operators to bank sampah with per-profile PIN for kiosk mode';

CREATE TABLE waste_categories (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    code            VARCHAR(20) NOT NULL UNIQUE,
    name_id         VARCHAR(255) NOT NULL,
    name_en         VARCHAR(255),
    category_group  VARCHAR(50) NOT NULL,
    unit_type       VARCHAR(20) NOT NULL DEFAULT 'kg',
    unit_price      BIGINT NOT NULL DEFAULT 0,
    photo_url       TEXT,
    sort_order      INTEGER NOT NULL DEFAULT 0,
    is_active       BOOLEAN NOT NULL DEFAULT true,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

COMMENT ON TABLE waste_categories IS 'Waste types with real photos for operator selection';
COMMENT ON COLUMN waste_categories.unit_price IS 'Default static price; overridden by price_references if active';

-- ──── Transactions ────

CREATE TABLE transactions (
    id                  UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    bank_sampah_id      UUID NOT NULL REFERENCES waste_banks(id),
    operator_id         UUID NOT NULL REFERENCES users(id),
    customer_id         UUID NOT NULL REFERENCES users(id),
    transaction_type    transaction_type NOT NULL DEFAULT 'deposit',
    status              transaction_status NOT NULL DEFAULT 'pending_sync',
    total_weight        BIGINT NOT NULL,
    total_value         BIGINT NOT NULL,
    device_timestamp    TIMESTAMPTZ,
    server_timestamp    TIMESTAMPTZ,
    sync_id             UUID,
    is_offline_created  BOOLEAN NOT NULL DEFAULT true,
    lamport_timestamp   BIGINT,
    hmac_signature      VARCHAR(128),
    price_snapshot      JSONB,
    weight_photo_url    TEXT,
    fraud_flag          BOOLEAN NOT NULL DEFAULT false,
    fraud_reason        TEXT,
    sms_sent            BOOLEAN NOT NULL DEFAULT false,
    sms_sent_at         TIMESTAMPTZ,
    created_at          TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at          TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

-- TimescaleDB hypertable (run after table creation)
-- SELECT create_hypertable('transactions', 'created_at', chunk_time_interval => INTERVAL '1 day');

COMMENT ON TABLE transactions IS 'Waste deposit transactions — the core business record';
COMMENT ON COLUMN transactions.total_weight IS 'Total weight in grams (integer)';
COMMENT ON COLUMN transactions.total_value IS 'Total monetary value in satuan rupiah (cents). INTEGER. NO FLOATS.';
COMMENT ON COLUMN transactions.price_snapshot IS 'JSON snapshot of waste_categories.unit_price at transaction time';
COMMENT ON COLUMN transactions.hmac_signature IS 'HMAC-SHA256 of transaction fields signed by device private key';

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

CREATE TABLE ledger_accounts (
    id                  UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    account_code        VARCHAR(20) NOT NULL UNIQUE,
    name_id             VARCHAR(255) NOT NULL,
    account_type        account_type NOT NULL,
    normal_balance      VARCHAR(4) NOT NULL CHECK (normal_balance IN ('debit', 'credit')),
    is_active           BOOLEAN NOT NULL DEFAULT true,
    created_at          TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

COMMENT ON TABLE ledger_accounts IS 'Chart of accounts for double-entry bookkeeping';

CREATE TABLE ledger_entries (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    account_id      UUID NOT NULL REFERENCES ledger_accounts(id),
    entry_type      entry_type NOT NULL,
    amount          BIGINT NOT NULL CHECK (amount > 0),
    balance_after   BIGINT NOT NULL,
    reference_type  VARCHAR(50) NOT NULL,
    reference_id    UUID NOT NULL,
    reason_code     VARCHAR(50) NOT NULL,
    actor_id        UUID REFERENCES users(id),
    description     TEXT,
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

-- ──── Sync & Metadata Tables ────

CREATE TABLE devices (
    id                  UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    bank_sampah_id      UUID NOT NULL REFERENCES waste_banks(id),
    device_name         VARCHAR(255),
    device_phone_number VARCHAR(20),
    device_pub_key      TEXT NOT NULL,
    device_fingerprint  VARCHAR(64),
    registered_at       TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    last_seen_at        TIMESTAMPTZ,
    is_active           BOOLEAN NOT NULL DEFAULT true,
    app_version         VARCHAR(20),
    created_at          TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at          TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

COMMENT ON TABLE devices IS 'Registered Android devices per bank sampah';

CREATE TABLE sync_log (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    device_id       UUID NOT NULL REFERENCES devices(id),
    status          sync_status NOT NULL DEFAULT 'in_progress',
    last_sync_cursor UUID,
    lamport_timestamp BIGINT,
    records_synced  INTEGER NOT NULL DEFAULT 0,
    records_failed  INTEGER NOT NULL DEFAULT 0,
    started_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    completed_at    TIMESTAMPTZ,
    error_message   TEXT,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

COMMENT ON TABLE sync_log IS 'Audit trail for every device-server sync session';

CREATE TABLE processed_ids (
    id              UUID PRIMARY KEY,
    entity_type     VARCHAR(50) NOT NULL,
    processed_at    TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

COMMENT ON TABLE processed_ids IS 'Idempotency dedup table. Checked before every mutation. Records retained 90 days.';

-- ──── SMS ────

CREATE TABLE sms_queue (
    id                  UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    transaction_id      UUID REFERENCES transactions(id),
    phone_to            VARCHAR(20) NOT NULL,
    message             TEXT NOT NULL,
    status              sms_status NOT NULL DEFAULT 'pending',
    provider            VARCHAR(50),
    provider_message_id VARCHAR(255),
    cost                BIGINT,
    retry_count         INTEGER NOT NULL DEFAULT 0,
    max_retries         INTEGER NOT NULL DEFAULT 3,
    last_error          TEXT,
    sent_at             TIMESTAMPTZ,
    created_at          TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

COMMENT ON TABLE sms_queue IS 'Queue of SMS messages for transaction receipts and OTP delivery';

-- ──── Compliance Tables ────

CREATE TABLE user_consent_log (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id         UUID NOT NULL REFERENCES users(id),
    consent_type    VARCHAR(50) NOT NULL,
    consent_version VARCHAR(20) NOT NULL,
    granted         BOOLEAN NOT NULL,
    ip_address      INET,
    device_id       UUID REFERENCES devices(id),
    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

COMMENT ON TABLE user_consent_log IS 'UU PDP Pasal 20 compliance: explicit consent tracking';

CREATE TABLE audit_log (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    action          audit_action NOT NULL,
    actor_id        UUID REFERENCES users(id),
    target_type     VARCHAR(50),
    target_id       UUID,
    changes         JSONB,
    ip_address      INET,
    user_agent      TEXT,
    request_id      UUID,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW()
) PARTITION BY RANGE (created_at);

COMMENT ON TABLE audit_log IS 'Immutable audit trail for all data access and administrative actions';

-- Audit log partitions (monthly)
CREATE TABLE audit_log_2026_07 PARTITION OF audit_log
    FOR VALUES FROM ('2026-07-01') TO ('2026-08-01');
CREATE TABLE audit_log_2026_08 PARTITION OF audit_log
    FOR VALUES FROM ('2026-08-01') TO ('2026-09-01');
CREATE TABLE audit_log_2026_09 PARTITION OF audit_log
    FOR VALUES FROM ('2026-09-01') TO ('2026-10-01');

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

-- ──── Price References ────

CREATE TABLE price_references (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    category_id     UUID NOT NULL REFERENCES waste_categories(id),
    region_id       INTEGER NOT NULL,
    region_type     VARCHAR(20) NOT NULL DEFAULT 'national',
    source          VARCHAR(100) NOT NULL,
    price_per_unit  BIGINT NOT NULL,
    effective_from  TIMESTAMPTZ NOT NULL,
    effective_to    TIMESTAMPTZ,
    version         INTEGER NOT NULL,
    is_current      BOOLEAN NOT NULL DEFAULT true,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

COMMENT ON TABLE price_references IS 'Historical price reference per category per region. Immutable — INSERT only.';

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
CREATE INDEX idx_sms_status ON sms_queue (status, retry_count);

-- Sync log
CREATE INDEX idx_sync_device ON sync_log (device_id, created_at DESC);

-- Processed IDs
CREATE INDEX idx_processed_ids_lookup ON processed_ids (id, entity_type);

-- Audit log
CREATE INDEX idx_audit_actor ON audit_log (actor_id, created_at DESC);
CREATE INDEX idx_audit_target ON audit_log (target_type, target_id);

-- Consent log
CREATE INDEX idx_consent_user_type ON user_consent_log (user_id, consent_type, created_at DESC);

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
