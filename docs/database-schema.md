# TAUT — Database Schema
## Complete Database Design
**Version:** 2.0
**Date:** 23 Juni 2026

---

## Table of Contents

1. [Entity Relationship Diagram](#1-entity-relationship-diagram)
2. [Core Tables](#2-core-tables)
3. [Financial Tables](#3-financial-tables)
4. [Sync & Metadata Tables](#4-sync--metadata-tables)
5. [Audit & Compliance Tables](#5-audit--compliance-tables)
6. [Materialized Views](#6-materialized-views)
7. [Index Design](#7-index-design)
8. [Migration Strategy](#8-migration-strategy)
9. [Partitioning Strategy](#9-partitioning-strategy)

---

## Design Principles

1. **UUIDv7 for all primary keys** — Time-ordered, collision-free, works offline
2. **Integer arithmetic only** — All monetary values in satuan rupiah (cents), stored as `BIGINT`
3. **TimescaleDB hypertables** for time-series data (`transactions`, `price_history`)
4. **Monthly partitioning** for `ledger_entries`
5. **Immutable append-only** financial tables — never UPDATE, only INSERT
6. **No soft deletes** — Use `is_active` status flags instead

---

## 1. Entity Relationship Diagram

```
┌──────────────────────┐       ┌──────────────────────────┐
│     waste_banks      │       │         users            │
│──────────────────────│       │──────────────────────────│
│ PK id (UUIDv7)       │       │ PK id (UUIDv7)           │
│    code (BSA-XXXX)   │──┐    │    phone_number (UNIQUE) │
│    name              │  │    │    role                   │
│    phone             │  │    │    name                   │
│    village_id        │  │    │    location_id            │
│    device_pub_key    │  │    │    kyc_status             │
│    is_active         │  │    │    pin_hash               │
│    created_at        │  │    │    pin_salt               │
│    updated_at        │  │    │    is_active              │
└──────────────────────┘  │    │    created_at             │
        │ 1               │    │    updated_at             │
        │                 │    └────────────┬───────────────┘
        │                 │                 │ 1
        │                 │                 │
        ▼ N               │                 ▼ N
┌──────────────────────┐  │    ┌──────────────────────────┐
│      transactions    │  │    │   operator_profiles       │
│──────────────────────│  │    │──────────────────────────│
│ PK id (UUIDv7)       │  │    │ PK id (UUIDv7)           │
│ FK waste_bank_id     │──┘    │ FK bank_sampah_id        │
│ FK operator_id       │──────▶│ FK user_id               │
│ FK customer_id       │───┐   │    pin_hash               │
│    transaction_type  │   │   │    is_primary             │
│    status            │   │   │    is_active              │
│    total_weight      │   │   │    created_at             │
│    total_value       │   │   └──────────────────────────┘
│    device_timestamp  │   │
│    server_timestamp  │   │           1
│    sync_id           │   │            │
│    is_offline_created│   │            │ N
│    lamport_timestamp │   │  ┌──────────────────────────┐
│    hmac_signature    │   │  │    transaction_items     │
│    price_snapshot    │   │  │──────────────────────────│
│    sms_sent          │   │  │ PK id (UUIDv7)           │
│    created_at        │   │  │ FK transaction_id        │
└──────────────────────┘   │  │ FK category_id           │
        │ 1                └──│    weight                 │
        │                    │    price_per_unit (LOCKED) │
        │ N                  │    total_value             │
┌──────────────────────┐    │    created_at              │
│    waste_categories  │    └──────────────────────────┘
│──────────────────────│
│ PK id (UUIDv7)       │            ┌──────────────────────────┐
│    code               │──────────▶│  price_references        │
│    name_id            │           │──────────────────────────│
│    name_en            │           │ PK id (UUIDv7)           │
│    category_group     │           │ FK category_id           │
│    unit_price         │           │    region_id             │
│    unit_type          │           │    source                │
│    photo_url          │           │    price_per_unit        │
│    sort_order         │           │    effective_from        │
│    is_active          │           │    effective_to          │
│    created_at         │           │    version (INT)         │
│    updated_at         │           │    created_at            │
└──────────────────────┘           └──────────────────────────┘

┌──────────────────────┐       ┌──────────────────────────┐
│   ledger_entries     │       │   qr_codes                │
│──────────────────────│       │──────────────────────────│
│ PK id (UUIDv7)       │       │ PK id (UUIDv7)           │
│ FK account_id        │       │ FK transaction_id        │
│    entry_type        │       │    qr_content (JSON)     │
│    amount (BIGINT)   │       │    qr_version             │
│    balance_after     │       │    printed_at             │
│    reference_type    │       │    verified_at            │
│    reference_id      │       │    expires_at             │
│    reason_code       │       │    created_at             │
│    actor_id          │       └──────────────────────────┘
│    created_at        │
└──────────────────────┘       ┌──────────────────────────┐
                               │   devices                 │
┌──────────────────────┐       │──────────────────────────│
│   sms_queue          │       │ PK id (UUIDv7)           │
│──────────────────────│       │ FK bank_sampah_id        │
│ PK id (UUIDv7)       │       │    device_name            │
│ FK transaction_id    │       │    device_phone_number    │
│    phone_to          │       │    device_pub_key         │
│    message           │       │    registered_at          │
│    status            │       │    last_seen_at           │
│    provider          │       │    is_active              │
│    provider_message_id│      │    created_at             │
│    cost (satuan)     │       └──────────────────────────┘
│    retry_count       │
│    sent_at           │       ┌──────────────────────────┐
│    created_at        │       │   user_consent_log        │
└──────────────────────┘       │──────────────────────────│
                               │ PK id (UUIDv7)           │
┌──────────────────────┐       │ FK user_id               │
│   sync_log            │       │    consent_type           │
│──────────────────────│       │    consent_version        │
│ PK id (UUIDv7)       │       │    granted (BOOLEAN)      │
│ FK device_id         │       │    ip_address              │
│    last_sync_cursor  │       │    created_at             │
│    records_synced    │       └──────────────────────────┘
│    records_failed    │
│    started_at        │       ┌──────────────────────────┐
│    completed_at      │       │   villages (reference)    │
│    error_message     │       │──────────────────────────│
│    created_at        │       │ PK id (INTEGER)          │
└──────────────────────┘       │    kecamatan_id           │
                               │    name_id                │
                               │    postal_code            │
                               │    created_at             │
                               └──────────────────────────┘
```

---

## 2. Core Tables

### 2.1 `waste_banks`

Stores registered bank sampah locations. Each bank sampah has a unique code and a device public key for HMAC verification.

```sql
CREATE TABLE waste_banks (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    code            VARCHAR(20) NOT NULL UNIQUE,  -- Format: BSA-XXXX
    name            VARCHAR(255) NOT NULL,          -- Nama bank sampah
    phone           VARCHAR(20) NOT NULL,           -- Contact phone number
    address         TEXT,
    village_id      INTEGER NOT NULL REFERENCES villages(id),
    photo_url       TEXT,                            -- Optional photo of the location
    device_pub_key  TEXT,                            -- Public key for HMAC verification
    is_active       BOOLEAN NOT NULL DEFAULT true,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

COMMENT ON TABLE waste_banks IS 'Registered bank sampah locations';
COMMENT ON COLUMN waste_banks.code IS 'Human-readable unique code: BSA-XXXX where XXXX is sequential';
COMMENT ON COLUMN waste_banks.device_pub_key IS 'Ed25519 or ECDSA public key for device-level HMAC-SHA256 signature verification';
COMMENT ON COLUMN waste_banks.village_id IS 'Reference to administrative village (kelurahan) — NOT GPS coordinates per UU PDP Pasal 16';
```

### 2.2 `users`

Stores all users: operators, customers, DLH staff, admins. Phone number is the primary identifier (consistent with SMS-based auth).

```sql
CREATE TYPE user_role AS ENUM (
    'operator',      -- Bank sampah operator (primary user)
    'customer',      -- Household nasabah (app+phone, minimal data)
    'dlh_staff',     -- Dinas Lingkungan Hidup (read-only access)
    'admin',         -- TAUT platform admin
    'superadmin'     -- Full system access (limited, audited)
);

CREATE TABLE users (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    phone_number    VARCHAR(20) NOT NULL UNIQUE,
    role            user_role NOT NULL DEFAULT 'customer',
    name            VARCHAR(255),                    -- Optional for customers (SMS-only)
    village_id      INTEGER REFERENCES villages(id), -- Kelurahan level only
    kyc_status      VARCHAR(20) NOT NULL DEFAULT 'unverified',  -- unverified, pending, verified, rejected
    pin_hash        VARCHAR(255),                    -- bcrypt hash (cost=10) for operator PIN
    pin_salt        VARCHAR(64),                     -- Salt for bcrypt
    failed_pin_attempts INTEGER NOT NULL DEFAULT 0,  -- Incremented on wrong PIN; auto-wipe at 10
    is_active       BOOLEAN NOT NULL DEFAULT true,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

COMMENT ON TABLE users IS 'All platform users: operators, customers, DLH, admins';
COMMENT ON COLUMN users.kyc_status IS 'Know-Your-Customer status for financial compliance (Fase 2+ for QRIS)';
COMMENT ON COLUMN users.failed_pin_attempts IS 'Security: triggers device auto-wipe at 10 failed attempts';
```

### 2.3 `operator_profiles`

Links operators to specific bank sampah devices. Supports kiosk mode (multiple operators per device).

```sql
CREATE TABLE operator_profiles (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    bank_sampah_id  UUID NOT NULL REFERENCES waste_banks(id) ON DELETE CASCADE,
    user_id         UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    pin_hash        VARCHAR(255) NOT NULL,           -- Per-profile PIN (bcrypt, cost=10)
    is_primary      BOOLEAN NOT NULL DEFAULT false,  -- Primary operator for this bank
    is_active       BOOLEAN NOT NULL DEFAULT true,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    
    UNIQUE(bank_sampah_id, user_id)
);

COMMENT ON TABLE operator_profiles IS 'Maps operators to bank sampah with per-profile PIN for kiosk mode';
COMMENT ON COLUMN operator_profiles.is_primary IS 'Only one primary operator per bank sampah; can manage other profiles';
```

### 2.4 `waste_categories`

Defines the types of waste that can be deposited. Uses real photos (not icons) for operator recognition.

```sql
CREATE TABLE waste_categories (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    code            VARCHAR(20) NOT NULL UNIQUE,   -- e.g., KRD-01 (Kardus), PLT-01 (Plastik PET)
    name_id         VARCHAR(255) NOT NULL,           -- Bahasa Indonesia name
    name_en         VARCHAR(255),                    -- English name (optional, for system)
    category_group  VARCHAR(50) NOT NULL,            -- kardus, plastik, kaca, logam, kertas, elektronik, lainnya
    unit_type       VARCHAR(20) NOT NULL DEFAULT 'kg', -- kg, pcs, liter
    unit_price      BIGINT NOT NULL DEFAULT 0,       -- Default price per unit in satuan rupiah
    photo_url       TEXT,                             -- URL to real photo of this waste type
    sort_order      INTEGER NOT NULL DEFAULT 0,      -- Display order (matters for operators)
    is_active       BOOLEAN NOT NULL DEFAULT true,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

COMMENT ON TABLE waste_categories IS 'Waste types with real photos for operator selection';
COMMENT ON COLUMN waste_categories.unit_price IS 'Default static price; overridden by price_references if active';
COMMENT ON COLUMN waste_categories.photo_url IS 'Real photo path (e.g., /categories/kardus.jpg) — NOT vector illustration';
```

### 2.5 `transactions`

The core business table. Each row is one deposit transaction made by a customer at a bank sampah. Uses TimescaleDB hypertable for time-series queries.

```sql
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

CREATE TABLE transactions (
    id                  UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    bank_sampah_id      UUID NOT NULL REFERENCES waste_banks(id),
    operator_id         UUID NOT NULL REFERENCES users(id),
    customer_id         UUID NOT NULL REFERENCES users(id),
    transaction_type    transaction_type NOT NULL DEFAULT 'deposit',
    status              transaction_status NOT NULL DEFAULT 'pending_sync',
    total_weight        BIGINT NOT NULL,             -- Total weight in grams (integer)
    total_value         BIGINT NOT NULL,             -- Total value in satuan rupiah
    device_timestamp    TIMESTAMPTZ,                 -- Device-reported time (advisory only)
    server_timestamp    TIMESTAMPTZ,                 -- Server-assigned time (authoritative)
    sync_id             UUID,                        -- UUID of the sync batch that sent this
    is_offline_created  BOOLEAN NOT NULL DEFAULT true,
    lamport_timestamp   BIGINT,                      -- Lamport clock for ordering
    hmac_signature      VARCHAR(128),                -- HMAC-SHA256 of transaction fields
    price_snapshot      JSONB,                       -- Frozen price list at time of transaction
    -- Anti-fraud fields
    weight_photo_url    TEXT,                         -- Photo of weight >10kg threshold
    fraud_flag          BOOLEAN NOT NULL DEFAULT false,
    fraud_reason        TEXT,
    -- SMS delivery
    sms_sent            BOOLEAN NOT NULL DEFAULT false,
    sms_sent_at         TIMESTAMPTZ,
    created_at          TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at          TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

-- TimescaleDB hypertable creation (run AFTER table creation)
-- SELECT create_hypertable('transactions', 'created_at', chunk_time_interval => INTERVAL '1 day');

COMMENT ON TABLE transactions IS 'Waste deposit transactions — the core business record';
COMMENT ON COLUMN transactions.total_weight IS 'Total weight in grams (convert kg→g for integer math: 5kg = 5000g)';
COMMENT ON COLUMN transactions.total_value IS 'Total monetary value in satuan rupiah (cents). INTEGER. NO FLOATS.';
COMMENT ON COLUMN transactions.price_snapshot IS 'JSON snapshot of waste_categories.unit_price at transaction time for audit trail';
COMMENT ON COLUMN transactions.hmac_signature IS 'HMAC-SHA256 of transaction fields signed by device private key for non-repudiation';
COMMENT ON COLUMN transactions.lamport_timestamp IS 'Server-assigned ordering timestamp; NULL until first sync';
COMMENT ON COLUMN transactions.weight_photo_url IS 'Anti-fraud: required if total_weight > 10000 (10kg). Photo of the waste on scale.';
COMMENT ON COLUMN transactions.fraud_flag IS 'Set by fraud detection system; reviewed by admin';
```

### 2.6 `transaction_items`

Line items within a transaction. Each item is one category of waste deposited.

```sql
CREATE TABLE transaction_items (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    transaction_id  UUID NOT NULL REFERENCES transactions(id) ON DELETE CASCADE,
    category_id     UUID NOT NULL REFERENCES waste_categories(id),
    weight          BIGINT NOT NULL,                 -- Weight in grams
    price_per_unit  BIGINT NOT NULL,                 -- Price per kg AT TRANSACTION TIME (LOCKED)
    total_value     BIGINT NOT NULL,                 -- (weight / 1000) * price_per_unit (integer math)
    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

COMMENT ON TABLE transaction_items IS 'Line items within a transaction — one per waste category deposited';
COMMENT ON COLUMN transaction_items.price_per_unit IS 'LOCKED: the price_per_unit at time of transaction, NOT the current price';
COMMENT ON COLUMN transaction_items.total_value IS 'Computed: (weight / 1000) * price_per_unit. Integer division. Remainder tracked in rounding_diff.';
```

### 2.7 `villages`

Reference table for administrative regions. Used instead of GPS coordinates for UU PDP compliance.

```sql
CREATE TABLE villages (
    id              INTEGER PRIMARY KEY GENERATED ALWAYS AS IDENTITY,
    kecamatan_id    INTEGER NOT NULL,               -- Kecamatan ID (from BPS/GoI standard)
    name_id         VARCHAR(255) NOT NULL,           -- Desa/kelurahan name
    postal_code     VARCHAR(10),
    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE TABLE kecamatans (
    id              INTEGER PRIMARY KEY GENERATED ALWAYS AS IDENTITY,
    kota_id         INTEGER NOT NULL,                -- Kota/kabupaten ID
    name_id         VARCHAR(255) NOT NULL,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE TABLE kotas (
    id              INTEGER PRIMARY KEY GENERATED ALWAYS AS IDENTITY,
    provinsi_id     INTEGER NOT NULL,
    name_id         VARCHAR(255) NOT NULL
);

CREATE TABLE provinsis (
    id              INTEGER PRIMARY KEY GENERATED ALWAYS AS IDENTITY,
    name_id         VARCHAR(255) NOT NULL,
    iso_code        VARCHAR(5)                      -- e.g., ID-JB for Jawa Barat
);

COMMENT ON TABLE villages IS 'Administrative villages (desa/kelurahan) — from BPS standard dataset. NOT GPS coordinates.';
```

---

## 3. Financial Tables

### 3.1 `ledger_accounts`

Chart of accounts for double-entry bookkeeping.

```sql
CREATE TYPE account_type AS ENUM (
    'asset',
    'liability',
    'equity',
    'revenue',
    'expense'
);

CREATE TABLE ledger_accounts (
    id                  UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    account_code        VARCHAR(20) NOT NULL UNIQUE,  -- e.g., '2000' for Customer Points
    name_id             VARCHAR(255) NOT NULL,
    account_type        account_type NOT NULL,
    normal_balance      VARCHAR(4) NOT NULL CHECK (normal_balance IN ('debit', 'credit')),
    is_active           BOOLEAN NOT NULL DEFAULT true,
    created_at          TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

-- Seed data
INSERT INTO ledger_accounts (account_code, name_id, account_type, normal_balance) VALUES
    ('1000', 'Kas — Rekening Operasional TAUT', 'asset', 'debit'),
    ('2000', 'Liabilitas — Poin Nasabah', 'liability', 'credit'),
    ('2100', 'Liabilitas — Payout Tertunda', 'liability', 'credit'),
    ('3000', 'Ekuitas — Laba Ditahan', 'equity', 'credit'),
    ('4000', 'Pendapatan — Komisi', 'revenue', 'credit'),
    ('4100', 'Pendapatan — Data-as-Service', 'revenue', 'credit'),
    ('5000', 'Beban — SMS', 'expense', 'debit'),
    ('5100', 'Beban — Redemption Poin', 'expense', 'debit'),
    ('5200', 'Beban — Operasional Platform', 'expense', 'debit');
```

### 3.2 `ledger_entries`

The immutable double-entry bookkeeping table. Every financial event creates two or more entries. Monthly-partitioned.

```sql
CREATE TYPE entry_type AS ENUM ('debit', 'credit');

CREATE TABLE ledger_entries (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    account_id      UUID NOT NULL REFERENCES ledger_accounts(id),
    entry_type      entry_type NOT NULL,
    amount          BIGINT NOT NULL CHECK (amount > 0),   -- Always positive; direction is entry_type
    balance_after   BIGINT NOT NULL,                      -- Running balance for this account after this entry
    reference_type  VARCHAR(50) NOT NULL,                  -- 'transaction', 'redemption', 'sms_cost', 'commission', etc.
    reference_id    UUID NOT NULL,                         -- FK to the source record
    reason_code     VARCHAR(50) NOT NULL,                  -- e.g., 'deposit_earn', 'points_use', 'sms_fee'
    actor_id        UUID REFERENCES users(id),             -- Who caused this entry
    description     TEXT,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW()
) PARTITION BY RANGE (created_at);

-- Monthly partitions
CREATE TABLE ledger_entries_2026_06 PARTITION OF ledger_entries
    FOR VALUES FROM ('2026-06-01') TO ('2026-07-01');
CREATE TABLE ledger_entries_2026_07 PARTITION OF ledger_entries
    FOR VALUES FROM ('2026-07-01') TO ('2026-08-01');
-- ... auto-generated by pg_partman or migration script

COMMENT ON TABLE ledger_entries IS 'Immutable double-entry bookkeeping. Every financial event creates ≥2 rows. NO UPDATES, only INSERT.';
COMMENT ON COLUMN ledger_entries.amount IS 'Always positive. Direction is encoded in entry_type (debit/credit). Integer. No floats.';
COMMENT ON COLUMN ledger_entries.balance_after IS 'Running balance of the account after this entry. Allows point-in-time balance queries without aggregation.';
COMMENT ON COLUMN ledger_entries.reason_code IS 'Machine-readable code for automated reconciliation: deposit_earn, redemption_cost, sms_fee, commission, payout, adj_*';
```

### 3.3 `sms_queue`

Tracks SMS messages pending or sent for transaction receipts and OTP.

```sql
CREATE TYPE sms_status AS ENUM (
    'pending',      -- Queued, not yet sent
    'sending',      -- Currently being sent
    'sent',         -- Successfully sent
    'failed',       -- Failed, retryable
    'bounced',      -- Carrier returned as undeliverable
    'cancelled'     -- Explicitly cancelled (e.g., duplicate)
);

CREATE TABLE sms_queue (
    id                  UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    transaction_id      UUID REFERENCES transactions(id),
    phone_to            VARCHAR(20) NOT NULL,
    message             TEXT NOT NULL,               -- <160 characters (1 SMS segment)
    status              sms_status NOT NULL DEFAULT 'pending',
    provider            VARCHAR(50),                 -- 'twilio', 'jatis', etc.
    provider_message_id VARCHAR(255),                -- Provider's message ID
    cost                BIGINT,                      -- Cost in satuan rupiah (integer)
    retry_count         INTEGER NOT NULL DEFAULT 0,
    max_retries         INTEGER NOT NULL DEFAULT 3,
    last_error          TEXT,
    sent_at             TIMESTAMPTZ,
    created_at          TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

COMMENT ON TABLE sms_queue IS 'Queue of SMS messages for transaction receipts and OTP delivery';
COMMENT ON COLUMN sms_queue.message IS 'Must be < 160 characters per budget constraint (avoid concatenation costs)';
COMMENT ON COLUMN sms_queue.cost IS 'Per-SMS cost from provider, tracked for expense accounting (ledger entry: DEBIT 5000, CREDIT 1000)';
```

---

## 4. Sync & Metadata Tables

### 4.1 `devices`

Tracks registered Android devices per bank sampah.

```sql
CREATE TABLE devices (
    id                  UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    bank_sampah_id      UUID NOT NULL REFERENCES waste_banks(id),
    device_name         VARCHAR(255),                 -- User-defined name ("HP Bank Sampah Melati 1")
    device_phone_number VARCHAR(20),                  -- Phone number of the device (for SMS sender ID)
    device_pub_key      TEXT NOT NULL,                -- Ed25519/ECDSA public key for HMAC verification
    device_fingerprint  VARCHAR(64),                  -- SHA-256 of device attestation
    registered_at       TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    last_seen_at        TIMESTAMPTZ,                  -- Last successful sync or API call
    is_active           BOOLEAN NOT NULL DEFAULT true,
    app_version         VARCHAR(20),                  -- Current installed app version
    created_at          TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at          TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

COMMENT ON TABLE devices IS 'Registered Android devices per bank sampah. Device key used for HMAC sync signing.';
COMMENT ON COLUMN devices.device_pub_key IS 'Public key for verifying HMAC-SHA256 signatures on sync payloads';
COMMENT ON COLUMN devices.last_seen_at IS 'Used for monitoring: alert if device not seen in > 48h';
```

### 4.2 `sync_log`

Records every sync session between a device and the server.

```sql
CREATE TYPE sync_status AS ENUM ('in_progress', 'completed', 'partial', 'failed');

CREATE TABLE sync_log (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    device_id       UUID NOT NULL REFERENCES devices(id),
    status          sync_status NOT NULL DEFAULT 'in_progress',
    last_sync_cursor UUID,                              -- Watermark: last UUIDv7 processed
    lamport_timestamp BIGINT,                           -- Lamport clock at sync end
    records_synced  INTEGER NOT NULL DEFAULT 0,
    records_failed  INTEGER NOT NULL DEFAULT 0,
    started_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    completed_at    TIMESTAMPTZ,
    error_message   TEXT,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

COMMENT ON TABLE sync_log IS 'Audit trail for every device-server sync session';
COMMENT ON COLUMN sync_log.last_sync_cursor IS 'Monotonically increasing UUIDv7-based cursor for delta sync';
```

### 4.3 `price_references`

Tracks price history per waste category per region. Each price change creates a new row (immutable history).

```sql
CREATE TABLE price_references (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    category_id     UUID NOT NULL REFERENCES waste_categories(id),
    region_id       INTEGER NOT NULL,                   -- Village ID or kota ID (NULL means national)
    region_type     VARCHAR(20) NOT NULL DEFAULT 'national', -- national, kota, kecamatan
    source          VARCHAR(100) NOT NULL,              -- 'admin', 'pengepul', 'system'
    price_per_unit  BIGINT NOT NULL,                    -- Price per kg in satuan rupiah
    effective_from  TIMESTAMPTZ NOT NULL,
    effective_to    TIMESTAMPTZ,                        -- NULL = currently active
    version         INTEGER NOT NULL,                   -- Monotonically increasing per (category_id, region_id)
    is_current      BOOLEAN NOT NULL DEFAULT true,      -- Denormalized flag for fast lookup
    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

COMMENT ON TABLE price_references IS 'Historical price reference per category per region. Immutable — INSERT only.';
COMMENT ON COLUMN price_references.effective_to IS 'NULL means this is the currently active price. Set to NOW() when superseded.';
COMMENT ON COLUMN price_references.version IS 'Per (category, region) version counter. Used for delta sync: device sends last_version, server sends changes.';

-- TimescaleDB hypertable for price history
-- SELECT create_hypertable('price_references', 'effective_from', chunk_time_interval => INTERVAL '30 days');
```

### 4.4 `qr_codes`

Tracks QR codes generated for transactions. Deferred to Fase 2 (camera scanning).

```sql
CREATE TABLE qr_codes (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    transaction_id  UUID NOT NULL REFERENCES transactions(id),
    qr_content      JSONB NOT NULL,                    -- { "tx_id": "...", "bank_code": "...", "amount": ..., "ts": "..." }
    qr_version      INTEGER NOT NULL DEFAULT 1,
    printed_at      TIMESTAMPTZ,
    verified_at     TIMESTAMPTZ,
    expires_at      TIMESTAMPTZ,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

COMMENT ON TABLE qr_codes IS 'QR codes for transaction verification (Fase 2+). QR content signed with device key.';
COMMENT ON COLUMN qr_codes.qr_content IS 'JSON payload: transaction ID, bank code, amount, timestamp signed with HMAC';
```

### 4.5 `user_consent_log`

Records consent events for UU PDP compliance.

```sql
CREATE TABLE user_consent_log (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id         UUID NOT NULL REFERENCES users(id),
    consent_type    VARCHAR(50) NOT NULL,              -- 'transaction_recording', 'dlh_data_sharing', 'terms_of_service'
    consent_version VARCHAR(20) NOT NULL,              -- Version of the consent document
    granted         BOOLEAN NOT NULL,                  -- true = accepted, false = revoked
    ip_address      INET,
    device_id       UUID REFERENCES devices(id),
    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

COMMENT ON TABLE user_consent_log IS 'UU PDP Pasal 20 compliance: explicit consent tracking';
COMMENT ON COLUMN user_consent_log.consent_type IS 'Consent category: transaction_recording, dlh_data_sharing, terms_of_service';
COMMENT ON COLUMN user_consent_log.granted IS 'Set to false if user revokes consent later (with audit trail)';
```

---

## 5. Audit & Compliance Tables

### 5.1 `audit_log`

Records all non-transaction data access events (for UU PDP Pasal 26 data subject rights tracking).

```sql
CREATE TYPE audit_action AS ENUM (
    'user_export',        -- Data subject accessed /export endpoint
    'user_forget',        -- Data subject requested deletion
    'admin_user_view',    -- Admin viewed user profile
    'admin_user_edit',    -- Admin modified user profile
    'device_register',    -- New device registered
    'device_wipe',        -- Remote wipe triggered
    'pin_change',         -- Operator PIN changed
    'price_update',       -- Price catalog updated
    'fraud_flag',         -- Transaction fraud-flagged
    'fraud_review'        -- Fraud flag reviewed/resolved
);

CREATE TABLE audit_log (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    action          audit_action NOT NULL,
    actor_id        UUID REFERENCES users(id),
    target_type     VARCHAR(50),                       -- 'user', 'transaction', 'device', 'price'
    target_id       UUID,                              -- ID of the affected entity
    changes         JSONB,                             -- { "field": "old_value", "new_value" }
    ip_address      INET,
    user_agent      TEXT,
    request_id      UUID,                              -- Correlation ID for tracing
    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW()
) PARTITION BY RANGE (created_at);

COMMENT ON TABLE audit_log IS 'Immutable audit trail for all data access and administrative actions';
COMMENT ON COLUMN audit_log.changes IS 'JSON diff of the change: {"pin_hash": {"old": "xxx", "new": "yyy"}}';
```

### 5.2 `processed_transactions`

Deduplication table. Tracks all UUIDv7 IDs that have been processed (idempotency enforcement).

```sql
CREATE TABLE processed_ids (
    id              UUID PRIMARY KEY,                   -- The UUIDv7 of the processed entity
    entity_type     VARCHAR(50) NOT NULL,               -- 'transaction', 'ledger_entry', etc.
    processed_at    TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    
    -- Bloom filter optimization: TTL = 90 days
    -- This table is checked BEFORE every insert; if UUID exists, skip
    -- Old records (>90d) are archived to GCS and removed from this table
);

COMMENT ON TABLE processed_ids IS 'Idempotency dedup table. Checked before every mutation. Records retained 90 days.';
COMMENT ON COLUMN processed_ids.id IS 'The UUIDv7 that was processed. Used for duplicate detection.';

-- Index for fast dedup lookup
CREATE INDEX idx_processed_ids_lookup ON processed_ids (id, entity_type);
```

---

## 6. Materialized Views

### 6.1 `mv_daily_rollup_by_bank`

```sql
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

-- Refresh every hour
CREATE UNIQUE INDEX idx_mv_daily_bank ON mv_daily_rollup_by_bank (day, bank_sampah_id, category_id);
```

### 6.2 `mv_weekly_rollup_by_kecamatan`

```sql
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
```

### 6.3 `mv_monthly_rollup_by_kota`

```sql
CREATE MATERIALIZED VIEW mv_monthly_rollup_by_kota AS
SELECT
    date_trunc('month', t.created_at) AS month,
    kt.id AS kota_id,
    ti.category_id,
    SUM(ti.weight) AS total_weight_grams,
    SUM(ti.total_value) AS total_value_satuan,
    COUNT(DISTINCT t.bank_sampah_id) AS active_banks,
    COUNT(DISTINCT t.customer_id) AS active_customers
FROM transactions t
JOIN transaction_items ti ON ti.transaction_id = t.id
JOIN waste_banks wb ON wb.id = t.bank_sampah_id
JOIN villages v ON v.id = wb.village_id
JOIN kecamatans kc ON kc.id = v.kecamatan_id
JOIN kotas kt ON kt.id = kc.kota_id
WHERE t.status IN ('synced', 'confirmed')
GROUP BY 1, 2, 3
ORDER BY 1 DESC, 2, 3;

CREATE UNIQUE INDEX idx_mv_monthly_kota ON mv_monthly_rollup_by_kota (month, kota_id, category_id);
```

### 6.4 `mv_daily_fraud_anomaly`

```sql
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
```

---

## 7. Index Design

### 7.1 Primary Indexes

| Table | Index Name | Column(s) | Type | Rationale |
|-------|-----------|-----------|------|-----------|
| `waste_banks` | `idx_waste_banks_code` | `code` | UNIQUE B-tree | Lookup by human-readable code |
| `users` | `idx_users_phone` | `phone_number` | UNIQUE B-tree | Primary lookup by phone (SMS auth) |
| `users` | `idx_users_role` | `role` | B-tree | Filter operators vs customers |
| `operator_profiles` | `idx_op_profile_bank_user` | `(bank_sampah_id, user_id)` | UNIQUE B-tree | One profile per bank+user |
| `transactions` | `idx_tx_bank_status` | `(bank_sampah_id, status)` | B-tree | Operator history view |
| `transactions` | `idx_tx_customer` | `(customer_id, created_at DESC)` | B-tree | Customer transaction history |
| `transactions` | `idx_tx_device_ts` | `(device_timestamp)` | B-tree | Offline sync ordering |
| `transactions` | `idx_tx_sync_batch` | `(sync_id)` | B-tree | Sync batch lookup |
| `transactions` | `idx_tx_lamport` | `(lamport_timestamp)` | B-tree | Lamport clock ordering |
| `transaction_items` | `idx_tx_items_tx` | `(transaction_id)` | B-tree | Join from transactions |
| `transaction_items` | `idx_tx_items_category` | `(category_id, created_at)` | B-tree | Category aggregation |
| `price_references` | `idx_price_current` | `(category_id, region_id)` WHERE `is_current=true` | Partial B-tree | Fast current price lookup |
| `price_references` | `idx_price_effective` | `(category_id, effective_from, effective_to)` | B-tree | Range queries for point-in-time pricing |
| `ledger_entries` | `idx_ledger_account` | `(account_id, created_at DESC)` | B-tree | Account balance history |
| `ledger_entries` | `idx_ledger_reference` | `(reference_type, reference_id)` | B-tree | Find entries for a transaction |
| `sms_queue` | `idx_sms_status` | `(status, retry_count)` | B-tree | Pick up pending SMS |
| `sync_log` | `idx_sync_device` | `(device_id, created_at DESC)` | B-tree | Last sync status per device |
| `audit_log` | `idx_audit_actor` | `(actor_id, created_at DESC)` | B-tree | User action history (Pasal 26) |
| `audit_log` | `idx_audit_target` | `(target_type, target_id)` | B-tree | Find all actions on an entity |
| `user_consent_log` | `idx_consent_user_type` | `(user_id, consent_type, created_at DESC)` | B-tree | Latest consent per user per type |

### 7.2 Partial and Covering Indexes

```sql
-- Only index active operators (95% of users are customers — no need to index them)
CREATE INDEX idx_active_operators ON users (phone_number) WHERE role = 'operator';

-- Covering index for operator dashboard (common query pattern)
CREATE INDEX idx_tx_operator_dashboard ON transactions (bank_sampah_id, created_at DESC)
    INCLUDE (total_weight, total_value, status, customer_id)
    WHERE status IN ('synced', 'confirmed');

-- Index for sync worker (find pending transactions efficiently)
CREATE INDEX idx_tx_pending_sync ON transactions (bank_sampah_id, device_timestamp)
    WHERE status = 'pending_sync'
    INCLUDE (id, total_weight, total_value, customer_id);

-- GiST index for price_snapshot JSONB (if we ever need to query frozen prices)
-- Not in MVP — JSONB is only for audit/reconciliation lookups
-- CREATE INDEX idx_tx_price_snapshot ON transactions USING GIN (price_snapshot jsonb_path_ops);
```

### 7.3 TimescaleDB-Specific Indexes

```sql
-- TimescaleDB automatically creates a space-time index on the hypertable dimension
-- Additional indexes for time-series queries:

-- Time-bucket queries (common for dashboards)
CREATE INDEX idx_tx_time_bucket ON transactions (time_bucket(INTERVAL '1 hour', created_at), bank_sampah_id);

-- Last N transactions per bank (operator "recent activity" view)
CREATE INDEX idx_tx_recent ON transactions (bank_sampah_id, created_at DESC) WHERE status = 'confirmed';

-- Price history query pattern
CREATE INDEX idx_price_history_lookup ON price_references (category_id, effective_from DESC);
```

---

## 8. Migration Strategy

### 8.1 Principles

1. **All migrations are versioned SQL files** — No ORM auto-migrations in production
2. **Forward-only** — No irreversible destructive changes without a plan
3. **Backward-compatible** — Deploy schema changes BEFORE code changes (additive-first)
4. **Rollback plan** — Every migration has a documented rollback
5. **Zero-downtime** — Migrations run while app is live (no locking)
6. **Environment parity** — Migrations tested on staging before production

### 8.2 Migration Tool

**Choice:** Flyway (community edition)

**Rationale:** 
- SQL-first (no DSL)
- Checksum-verified migration integrity
- Works with PostgreSQL, TimescaleDB
- Simple integration with CI/CD pipeline
- Supports repeatable migrations for views/functions

**Alternatives considered:** Liquibase (too complex for MVP), Alembic (Python, not aligned with Kotlin stack), manual SQL (no versioning).

### 8.3 Migration File Naming

```
V{version}__{description}.sql

Examples:
V1_0_0__initial_schema.sql
V1_0_1__add_device_fingerprint.sql
V1_1_0__add_points_tables.sql
V2_0_0__add_qris_tables.sql
```

### 8.4 MVP Migrations (Fase 0)

```sql
-- V1_0_0__initial_schema.sql
-- Core entity tables
-- Includes: villages, waste_banks, users, operator_profiles, waste_categories,
--           transactions, transaction_items, devices
-- Financial tables
-- Includes: ledger_accounts, ledger_entries (with partitions)
-- Sync tables
-- Includes: sync_log, processed_ids, sms_queue
-- Compliance tables
-- Includes: user_consent_log
-- Materialized views
-- Includes: mv_daily_rollup_by_bank, mv_weekly_rollup_by_kecamatan

-- V1_0_1__seed_reference_data.sql
-- INSERT INTO provinsis: 38 provinces
-- INSERT INTO kotas: 514 cities (seed from BPS dataset)
-- INSERT INTO kecamatans: ~7,200 districts
-- INSERT INTO villages: ~83,000 villages
-- INSERT INTO waste_categories: 20 core categories with photos
-- INSERT INTO ledger_accounts: standard chart of accounts

-- V1_0_2__add_price_references.sql
-- CREATE TABLE price_references
-- CREATE hypertable
-- Create partial index for current prices

-- V1_0_3__add_audit_log.sql
-- CREATE audit_action type
-- CREATE TABLE audit_log with partitions
-- Create indexes
```

### 8.5 Migration Deployment Process

```
1. Developer writes migration SQL in {version}__{desc}.sql
2. PR review: architect + one other engineer
3. Merged to main → CI runs migration against staging DB
4. CI validates:
   a. Migration applies cleanly
   b. Rollback works (if provided)
   c. No data loss or integrity violations
   d. Performance: run EXPLAIN ANALYZE on heavy queries
5. If staging passes → deploy to production:
   a. Create read replica (if migration is potentially heavy)
   b. Run migration on replica first
   c. Promote replica to primary
   d. Run migration on old primary (now replica)
   e. Verify checksums match

For destructive changes (DROP COLUMN, etc.):
   a. Release 1: Add new column + dual-write (old + new)
   b. Release 2: Backfill data
   c. Release 3: Migrate reads to new column
   d. Release 4: Drop old column
   Each release is 2-7 days apart.
```

### 8.6 Schema Evolution Safety

| Change Type | Safe Online? | Method |
|-------------|-------------|--------|
| ADD COLUMN (nullable, no default) | ✅ Yes | Simple DDL, no lock |
| ADD COLUMN (with default) | ⚠️ Caution | Use DEFAULT NULL, backfill in batch |
| CREATE INDEX | ✅ Yes | CONCURRENTLY keyword |
| DROP INDEX | ✅ Yes | Simple DDL |
| ADD CONSTRAINT (FK) | ⚠️ Caution | NOT VALID, then VALIDATE |
| DROP COLUMN | ❌ No | Multi-release strategy |
| RENAME COLUMN | ❌ No | Add new, drop old multi-release |
| ALTER COLUMN TYPE | ❌ No | Add new column, migrate data |
| ADD PARTITION | ✅ Yes | TimescaleDB native |
| CREATE HYPERTABLE | ✅ Yes | TimescaleDB non-blocking |

---

## 9. Partitioning Strategy

### 9.1 TimescaleDB Hypertables

| Table | Chunk Interval | Compression | Retention | Notes |
|-------|---------------|-------------|-----------|-------|
| `transactions` | 1 day | After 30 days (native compression) | Indefinite | Core table; chunks are small for fast queries |
| `price_references` | 30 days | After 90 days | Indefinite | Small table; compression optional |
| `transaction_items` | 1 day | After 30 days | Indefinite | Child table; align with transactions |

### 9.2 Native Partitioning (PostgreSQL)

| Table | Partition Key | Interval | Notes |
|-------|--------------|----------|-------|
| `ledger_entries` | RANGE (created_at) | Monthly | 12-24 partitions active; auto-create via pg_partman |
| `audit_log` | RANGE (created_at) | Monthly | Same strategy as ledger_entries |
| `sms_queue` | RANGE (created_at) | Weekly | Small data; kept for 90 days, then archived |

### 9.3 Partition Management (pg_partman)

```sql
-- Automate monthly partition creation for ledger_entries
SELECT partman.create_parent(
    p_parent_table := 'public.ledger_entries',
    p_control := 'created_at',
    p_type := 'native',
    p_interval := '1 month',
    p_premake := 3
);

-- Automate weekly partition creation for sms_queue
SELECT partman.create_parent(
    p_parent_table := 'public.sms_queue',
    p_control := 'created_at',
    p_type := 'native',
    p_interval := '1 week',
    p_premake := 4
);
```

### 9.4 Data Archival Strategy

| Table | Archive Trigger | Archive Method | Access After Archive |
|-------|----------------|---------------|---------------------|
| `transactions` (raw) | > 1 year | TimescaleDB native compression | Queryable (compressed) |
| `transaction_items` | > 1 year | Same chunk tree | Queryable via parent |
| `ledger_entries` | > 1 year | Detach partition → GCS | Restore partition for audit |
| `audit_log` | > 1 year | Detach partition → GCS | Restore partition for compliance |
| `sms_queue` | > 90 days | Delete (after confirmed sent) | Summary in ledger_entries |
| `sync_log` | > 90 days | Detach → GCS coldline | Rarely accessed |
| `processed_ids` | > 90 days | Delete (Bloom filter aged out) | Not needed after TTL |

---

*End of Database Schema Document*
