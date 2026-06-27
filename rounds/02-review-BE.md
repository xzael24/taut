# Round 2: Backend Engineering Review

---

## Role: **Backend Engineer**

**Project: TAUT**
**Review Date:** 23 June 2026

---

## Overall Assessment

The proposal is ambitious and addresses a real, painful gap. The offline-first mobile-first vision is correct for the target audience. But from a backend engineering standpoint, the proposal **glosses over the hardest problems entirely** — sync architecture, conflict resolution, financial transaction integrity, and the sheer scale of user onboarding. Below I break down each critical area, what's missing, what's risky, and what must change.

---

## 1. Sync Architecture for 11K+ Offline-First Devices

### What the Proposal Says
> "Sinkronisasi data via periodic sync when online"

### What That Actually Means (BE Perspective)
This is the single most complex backend challenge in the entire system. "Periodic sync" is not an architecture — it's a handwave over a problem that will determine whether TAUT works or doesn't.

**11,000+ bank sampah operators, each recording transactions offline, syncing whenever connectivity resumes.** This is textbook distributed systems — and the proposal has zero detail on how it works.

### Key Questions the Proposal Must Answer

**a) Conflict Resolution Strategy**
- Two operators at the same bank sampah could both be recording transactions offline on separate devices. What happens when they sync?
- A price catalog update is pushed while an operator is mid-transaction offline. Which version do they use?
- An operator corrects a past transaction offline. Server already has it. How do we resolve?

**Recommended approach:**
- **Optimistic locking with vector clocks or hybrid logical clocks (HLC).** Each device tracks its own logical timestamp. Conflicts are detected on sync and resolved based on entity type:
  - **Transactions:** First-write-wins with append-only semantics (never edit a finalized transaction; use compensating entries instead)
  - **Price catalogs:** Server-authoritative — devices always accept server's latest on sync
  - **User profile data:** Last-write-wins with field-level merging
  - **Inventory/stock levels:** CRDTs (Conflict-free Replicated Data Types) for additive counters, or server-authoritative recalculation after sync

**b) Sync Protocol**
- Delta sync vs. full sync? Must be **delta-only** — these are Rp500K phones on 2G/3G. Payloads must be <50KB per sync round.
- Sync should be **multipart**: upload local changes first, then download server changes. If upload fails partway through, it must be idempotent and resumable.
- Queue sync operations locally (SQLite) and process them in order. Use a background service, not a foreground sync block.

**c) Sync Triggers**
- "Periodic" is not enough. Sync should happen on: app open, after transaction completion, on network reconnect, and on a configurable schedule (e.g., once per hour when on WiFi, every 4 hours on cellular to save data).
- Need a lightweight heartbeat/ping to detect connectivity cheaply (no full HTTP handshake).

**d) Offline Data Storage**
- Local SQLite database with WAL mode for concurrent read/write.
- Local transaction queue with status: `pending_sync → syncing → synced → confirmed`.
- On sync failure: retry with exponential backoff. Never delete local data until server confirms receipt.

### Verdict
**The proposal must define a sync architecture before any code is written.** Without it, we will build a system where data silently duplicates, gets lost, or conflicts are resolved by whoever syncs last — which is unacceptable for financial transactions.

---

## 2. API Design for QRIS Integration

### What the Proposal Says
> "Poin bisa ditukar ke e-money / QRIS (via ShopeePay, GoPay, LinkAja, atau transfer bank)"

### What This Actually Requires

**This is NOT a simple feature — it's a regulated financial integration that determines the trustworthiness of the entire platform.**

### Architecture Required

**a) QRIS Payment Gateway**
- QRIS (Quick Response Indonesian Standard) is handled by Bank Indonesia via switching networks (RINTIS, Artajasa, Garda, etc.).
- TAUT cannot implement QRIS directly. Must integrate with a **licensed Payment Service Provider (PSP)** that supports QRIS:
  - Options: Midtrans (GoPay), Xendit, DOKU, or Bank Mandiri's QRIS API
  - Need both **QRIS Static** (for merchants/bank sampah) and **QRIS Dynamic** (for point redemption by individual users)
- API flow for point-to-cash redemption:
  ```
  User requests redemption → TAUT backend validates balance
  → Creates payout request to PSP → PSP generates QR code or pushes e-money
  → TAUT backend records transaction → User receives funds
  ```

**b) Account Management**
- Users need to link an e-money account or bank account. This requires KYC (Know Your Customer) under OJK regulations.
- For Pemulung: e-KTP verification, phone number linking. This is a significant onboarding friction for the target demographic.
- Minimum viable approach: allow QRIS-based redemption only to accounts already linked to the user's phone number.

**c) Financial Reconciliation**
- Every point redemption must be reconciled against the PSP settlement. Daily batch reconciliation job required.
- Need a ledger system (double-entry bookkeeping) for all financial flows:
  - Points issued (liability)
  - Points redeemed (liability reduction + cash outflow)
  - Commission collected (revenue)
- This is **not optional** — it's legally required under OJK rules for platforms handling financial flows.

### API Design Requirements
- All financial APIs must use **idempotency keys** — a sync retry must never double-process a redemption.
- Webhook-based confirmation from PSP, not polling. Handle webhook failures with retry and manual reconciliation.
- Rate limiting on redemption endpoints to prevent fraud/abuse.

### Verdict
**QRIS integration alone could take 6-8 weeks for a small team, including KYC, PSP integration, reconciliation, and regulatory compliance. The proposal treats this as a Phase 2 afterthought. It should be a Phase 0 foundation — or at minimum, the financial ledger architecture must be designed from day one, with QRIS integration pluggable later.**

---

## 3. Database & Data Modeling

### What the Proposal Needs

#### a) Waste Categories

The proposal mentions "50+ kategori sampah anorganik" and "20 kategori utama" for MVP. This requires:

```sql
-- Flexible, versioned category system
CREATE TABLE waste_categories (
    id UUID PRIMARY KEY,
    code VARCHAR(20) UNIQUE NOT NULL,        -- e.g., "PET_01"
    name_id VARCHAR(100) NOT NULL,           -- "Botol Plastik PET"
    name_en VARCHAR(100),
    category_group VARCHAR(50) NOT NULL,     -- "plastik", "kertas", "logam", "kaca"
    unit VARCHAR(10) DEFAULT 'kg',
    is_active BOOLEAN DEFAULT TRUE,
    sort_order INT,
    created_at TIMESTAMPTZ,
    updated_at TIMESTAMPTZ
);

-- Price reference — MUST be time-series, not a single "current price"
CREATE TABLE price_references (
    id UUID PRIMARY KEY,
    category_id UUID REFERENCES waste_categories(id),
    region_id UUID,                          -- per-kabupaten pricing
    source VARCHAR(50),                      -- "market_avg", "partner_X", "dlh_reference"
    price_per_unit DECIMAL(12,2) NOT NULL,
    currency VARCHAR(3) DEFAULT 'IDR',
    effective_from TIMESTAMPTZ NOT NULL,
    effective_to TIMESTAMPTZ,
    created_by UUID,
    version INT DEFAULT 1
);
```

**Critical: Price data must be a time series.** Prices change daily. An operator recording a transaction at 9 AM offline must record the price that was valid at that moment, not the price from whatever day they sync. Price snapshots must be bundled with each sync payload.

#### b) Transactions

```sql
-- Core transaction record
CREATE TABLE transactions (
    id UUID PRIMARY KEY,
    bank_sampah_id UUID NOT NULL,
    operator_id UUID NOT NULL,
    customer_id UUID,                        -- the depositor (may be anonymous)
    transaction_type VARCHAR(20) NOT NULL,    -- "deposit", "sale_to_pengepul", "redemption"
    status VARCHAR(20) DEFAULT 'pending',    -- pending, synced, confirmed, disputed, voided
    total_weight DECIMAL(10,3) NOT NULL,      -- kg
    total_value DECIMAL(12,2),               -- calculated from price at time of transaction
    device_id VARCHAR(50),                   -- which device created this
    device_timestamp TIMESTAMPTZ,            -- time on the device (may differ from server)
    server_timestamp TIMESTAMPTZ DEFAULT NOW(),
    sync_id UUID,                            -- groups items in a single sync batch
    is_offline_created BOOLEAN DEFAULT FALSE,
    created_at TIMESTAMPTZ,
    updated_at TIMESTAMPTZ
);

-- Line items per transaction (multiple waste types per deposit)
CREATE TABLE transaction_items (
    id UUID PRIMARY KEY,
    transaction_id UUID REFERENCES transactions(id),
    category_id UUID REFERENCES waste_categories(id),
    weight DECIMAL(10,3) NOT NULL,
    price_per_unit DECIMAL(12,2) NOT NULL,   -- LOCKED at transaction time
    total_value DECIMAL(12,2) NOT NULL,
    created_at TIMESTAMPTZ
);
```

#### c) Users & Roles

```sql
-- 3-4M users: this table will be massive
CREATE TABLE users (
    id UUID PRIMARY KEY,
    phone_number VARCHAR(15) UNIQUE NOT NULL,
    role VARCHAR(20) NOT NULL,               -- "operator", "pemulung", "customer", "pengepul", "dlh", "admin"
    name VARCHAR(100),
    location_id UUID,                        -- kelurahan/kecamatan
    is_verified BOOLEAN DEFAULT FALSE,
    is_active BOOLEAN DEFAULT TRUE,
    kyc_status VARCHAR(20),                  -- pending, verified, rejected
    pin_hash VARCHAR(255),                   -- for offline authentication
    created_at TIMESTAMPTZ,
    updated_at TIMESTAMPTZ
);

-- Partition users by role for query performance
-- 3-4M pemulung + 50K operators + millions of household depositors
CREATE INDEX idx_users_role_location ON users(role, location_id);
CREATE INDEX idx_users_phone ON users(phone_number);
```

#### d) Points & Ledger (Double-Entry)

```sql
CREATE TABLE ledger_entries (
    id UUID PRIMARY KEY,
    account_id UUID NOT NULL,                -- user's points account
    entry_type VARCHAR(20) NOT NULL,         -- "credit", "debit"
    amount DECIMAL(12,2) NOT NULL,           -- points
    reference_type VARCHAR(30),              -- "transaction", "redemption", "admin_adjustment"
    reference_id UUID,
    description TEXT,
    balance_after DECIMAL(12,2) NOT NULL,    -- running balance for audit
    created_at TIMESTAMPTZ
) PARTITION BY RANGE (created_at);           -- monthly partitioning for performance
```

### Scalability Concern
- **4M pemulung × 50K operators = potential 5-10M users at maturity.** User table needs sharding strategy from day one. Geographic sharding (by provinsi) is most logical.
- **Transaction volume:** Assuming 11K bank sampah × 20 transactions/day average = **220K transactions/day**. That's ~2.5 TPS average but likely concentrated in 8 AM–2 PM window, so **peak of 15-20 TPS**. Manageable, but requires connection pooling and read replicas for the dashboard.
- **Price data:** 50 categories × 514 kabupaten × daily updates = ~25K price records/day. Not huge, but query patterns must use composite indexes on (category_id, region_id, effective_from).

---

## 4. Dashboard for DLH (Government)

### What the Proposal Says
> "Pemerintah daerah mendapat data agregat real-time tentang tingkat daur ulang per kecamatan"

### What DLH Actually Needs (from a BE data perspective)

**a) Data Requirements:**
| Metric | Granularity | Frequency |
|--------|------------|-----------|
| Total tonnage deposited per kecamatan | Daily | Near real-time |
| Waste composition breakdown (by category) | Weekly | Daily rollup |
| Number of active bank sampah | Monthly | On-change |
| Number of active operators/pemulung | Monthly | On-change |
| Recycling rate (diverted from TPA) | Monthly | Calculated |
| Transaction count and value | Daily | Near real-time |
| Compliance with reporting mandates | Quarterly | Calculated |

**b) Data Format & Delivery:**
- **REST API** for dashboard frontend (real-time views)
- **Scheduled CSV/PDF export** for government reporting (many DLH staff won't use dashboards; they need files)
- **Bulk data API** (paginated JSON) for integration with national systems (SIPSN — Sistem Informasi Pengelolaan Sampah Nasional)
- **Webhook/event system** to push significant data changes to DLH systems

**c) Aggregation Architecture:**
- **Do NOT build the dashboard by querying transaction tables directly.** With 220K+ transactions/day, dashboard queries will kill performance.
- Build an **OLAP-style aggregation layer:**
  - Materialized views refreshed on schedule (hourly for near-real-time)
  - Pre-computed rollups: daily, weekly, monthly by region × category
  - Separate analytics database (or read replica) so dashboard queries never compete with transactional workloads
  - Use TimescaleDB or ClickHouse for time-series analytics, or stick with PostgreSQL + materialized views if budget is tight

**d) Government Access Control:**
- DLH users should have **read-only** access scoped to their kabupaten/kota
- API keys + OAuth2 for automated integrations
- Audit log for all data access (government data access is politically sensitive)

### Verdict
**The dashboard is underspecified. It needs a proper data warehouse / analytics layer, not just "a dashboard web dasar." Without it, the first time DLH tries to query all 514 kabupaten for a quarterly report, the system will crawl or crash.**

---

## 5. Scalability — Sudden Sync Spikes

### The Problem No One Is Talking About

**When 11K devices sync simultaneously, you get a thundering herd.** When does this happen?

- **Morning rush:** Bank sampah operators open the app at 7-8 AM, all syncing yesterday's transactions.
- **After connectivity outage:** Internet goes down in a region (common in rural Indonesia), comes back up. All devices in that region sync at once.
- **After app update:** App update forces a full data resync.

**Estimating the spike:**
- 11K devices × ~20 transactions each = ~220K transactions to process
- Each transaction = ~5 API calls (upload, confirm, price sync, category sync, user sync)
- **Spike: ~1M API requests in a 15-30 minute window**

### Recommended Architecture

**a) API Gateway with Rate Limiting**
- Token bucket per device: max 100 requests/minute
- Queue excess requests; devices retry automatically

**b) Message Queue for Transaction Ingestion**
- API receives sync batch → writes to message queue (Redis Streams, RabbitMQ, or AWS SQS)
- Worker pool processes queue asynchronously
- Returns `accepted` immediately; device polls for confirmation or receives push notification
- This decouples sync reception from processing

**c) Horizontal Scaling**
- Stateless API servers behind a load balancer (can scale to 20+ instances)
- Database connection pooling (PgBouncer) — max connections per region
- Read replicas for dashboard queries

**d) CDN & Edge Caching**
- Price catalogs and category lists: cache at edge, update when pushed
- These are read-heavy, write-rare — perfect for CDN

### What the Proposal Must Address
**The proposal has no mention of infrastructure, deployment architecture, or expected traffic patterns.** For a system that will be used by 50,000+ operators across the Indonesian archipelago — often on unreliable networks — this is a critical gap.

---

## 6. Handling 3-4M Pemulung as Users

### Scale Challenges

**a) Authentication**
- The proposal uses SMS OTP. At 3-4M users:
  - SMS costs: ~Rp200-500/SMS × millions of verifications = significant cost
  - SMS delivery in rural Indonesia: unreliable, delayed
- **Better approach for MVP:**
  - Phone number + PIN for returning users (PIN stored as bcrypt hash locally on device, verified on first sync)
  - SMS OTP only for initial registration and password reset
  - Consider USSD fallback for feature phones (Phase 2)

**b) User Data Model**
- Pemulung are not bank account holders. Many share phones. Identity is fluid.
- Design for **phone-number-as-primary-key** with ability to transfer account to new device
- Allow **anonymous deposits** (household don't need account, bank sampah credits their own account)
- Pemulung profiles: lightweight. Name, phone, location, linked bank account (optional). Don't over-engineer KYC for Phase 1.

**c) Database Sharding**
- At 4M users, single PostgreSQL instance won't handle write load comfortably
- **Shard by geographic region (provinsi/kabupaten)** — this aligns with natural data access patterns (DLH queries by region, users are geographically distributed)
- Consider TiDB or CockroachDB for distributed SQL, or manual sharding with Vitess

**d) Cost Model**
- Cloud hosting for 4M users + 220K daily transactions:
  - Compute: 8-16 API servers (2-4 vCPU each) = ~$400-800/month on cloud
  - Database: Managed PostgreSQL (RDS/Cloud SQL) 2-4 instances = ~$600-1200/month
  - Storage: Transaction data ~50GB/year = negligible
  - SMS: ~$500-2000/month depending on volume
  - CDN: ~$100-200/month
  - **Total: ~$1,600-4,400/month at 4M users** — manageable, but must be budgeted

---

## 7. Security for Financial Transactions

### Threat Model

| Threat | Severity | Mitigation |
|--------|----------|------------|
| Replay attacks (re-submitting sync to duplicate points) | **CRITICAL** | Idempotency keys on every financial operation; server-side dedup |
| Transaction tampering on device | **HIGH** | HMAC-signed transactions (device has secret key, server verifies) |
| Price manipulation (operator records wrong price offline) | **HIGH** | Server validates transaction price against snapshot price ± tolerance |
| Unauthorized point redemption | **CRITICAL** | PIN + phone verification for redemptions; rate limiting; anomaly detection |
| Bulk fraud (colluding operators inflating weight) | **MEDIUM** | Statistical anomaly detection: flag operators with weight/volume ratios far above average for their region |
| Man-in-the-middle on sync | **HIGH** | TLS everywhere; certificate pinning on Android app |
| Database breach | **CRITICAL** | Encrypt PII at rest; separate financial data from user data; audit logging; SOC 2 compliance path |

### Financial Compliance
- **Bank Indonesia regulations** apply if TAUT handles e-money. If TAUT is a **pass-through** to licensed PSPs, regulatory burden is lower — but must structure the business correctly.
- **UU PDP (Indonesian Personal Data Protection Law)** requires consent, data minimization, and breach notification. All user data must be encrypted, and processing must have legal basis.
- **Every financial operation must be auditable.** Immutable transaction log. No soft deletes. Admin actions logged with actor, timestamp, and justification.

### Specific Recommendations
1. **Double-entry ledger from day one.** Not a points counter — a real ledger with credit/debit entries, balance tracking, and reconciliation.
2. **Idempotency keys** on all mutation endpoints. Device generates a UUID per operation; server checks before processing.
3. **Signed payloads** from device: each sync batch should include HMAC-SHA256 signature using a device-specific key. Server verifies before accepting. Prevents device compromise from injecting fake transactions.
4. **Circuit breakers** on PSP integrations. If QRIS gateway is down, queue redemption requests and process when gateway recovers — never lose user funds.

---

## 8. Trickiest BE Challenge

### Without Question: Offline-First Sync with Financial Integrity

The intersection of these three requirements creates the hardest problem:

1. **Offline-first**: Transactions created on devices without server validation
2. **Financial integrity**: Every peso must be accounted for, no double-spending, no loss
3. **Conflict resolution**: Multiple devices, unreliable networks, potential data tampering

**The nightmare scenario:**
> An operator records 50 kg of plastic bottles offline. They sync. But their phone's clock is wrong — it's showing yesterday's date. The price catalog on the server has been updated. Do we use yesterday's price or today's? If yesterday's price was higher and we use today's lower price, the operator (or their customer) loses money. If we use yesterday's higher price, we might be enabling arbitrage.

**Another nightmare:**
> An operator's sync fails midway. 30 of 50 transactions are uploaded, but the API server crashes on transaction #31. On retry, the app doesn't know which transactions were accepted. It resends all 50. We now have 30 duplicates and 20 new ones. Without idempotency, the user gets double points. With bad idempotency, the last 20 are lost.

**This is solvable, but it requires careful, deliberate engineering that the proposal doesn't acknowledge.**

My estimate: **sync architecture alone is 30-40% of the total backend effort for MVP.** If under-scoped, the entire system fails.

---

## 9. What MUST Change Before We Proceed

### 🔴 Critical Changes (Blocking)

1. **Define the sync architecture explicitly.** Include:
   - Conflict resolution strategy per entity type
   - Sync protocol (delta, multipart, idempotent)
   - Offline data model (local SQLite schema)
   - Sync payload format and size constraints
   - Error handling and retry logic
   - **This should be a separate design document, not a one-liner in the proposal.**

2. **Design the financial ledger system.** Even without QRIS integration in MVP:
   - Double-entry ledger schema
   - Points issuance and redemption flow
   - Idempotency and deduplication strategy
   - Reconciliation process
   - **Without this, adding QRIS later will require rewriting the financial layer.**

3. **Establish the analytics/OLAP layer.** The DLH dashboard cannot be built on top of transactional tables:
   - Define aggregation pipeline (raw → summary → dashboard)
   - Choose technology (materialized views vs. dedicated analytics DB)
   - Define API contracts for dashboard data

### 🟡 Important Changes (Should Block)

4. **Define API contracts.** Before any frontend/backend work begins:
   - OpenAPI/Swagger spec for core endpoints (auth, transactions, sync, prices)
   - Request/response schemas
   - Error code conventions
   - Versioning strategy (URL-based: `/api/v1/`)

5. **Infrastructure design.** Minimum:
   - Deployment topology (cloud provider, regions, number of environments)
   - Database architecture (primary, replicas, analytics)
   - Caching strategy
   - CDN configuration
   - CI/CD pipeline

6. **User onboarding flow for low-literacy users.** Backend implications:
   - Simplified registration (phone + PIN, not email + password)
   - Device binding and transfer process
   - Recovery flow (lost phone, SIM swap)
   - Offline registration capability

### 🟢 Nice to Have (Can Wait)

7. **Data retention policy.** How long to keep raw transaction data? Indonesian regulations + UU PDP require defined retention periods.

8. **Disaster recovery plan.** Database backup frequency, RTO/RPO targets, multi-region failover.

---

## Summary

The TAUT proposal correctly identifies the problem and the right audience. The product vision is solid. But from a backend engineering standpoint, **the proposal is dangerously thin on the technical architecture that will make or break this system.** The offline-first sync architecture is not a feature — it IS the system. The financial ledger is not an add-on — it's the foundation of trust.

If I had to allocate backend engineering effort for MVP, it would look like:

| Component | % of BE Effort | Notes |
|-----------|----------------|-------|
| Offline sync architecture | 30-25% | Sync engine, conflict resolution, local DB |
| Financial ledger & transaction engine | 25% | Double-entry, idempotency, reconciliation |
| API layer & authentication | 15% | REST API, auth, rate limiting, validation |
| Price catalog & category service | 10% | CRUD + time-series queries + sync bundle |
| Analytics/DLH aggregation | 10% | Rollup pipeline, materialized views |
| Infrastructure & DevOps | 10% | Deployment, monitoring, alerting |

**The proposal asks for a 3-month MVP. With the current level of architectural detail, I estimate 5-6 months is realistic — and that's if we resolve the sync and ledger architecture questions immediately.**

My recommendation: **Add a "Phase 0" (4 weeks) focused entirely on architecture design — sync protocol, data model, API contracts, and infrastructure.** Build nothing until these decisions are made and documented. The cost of a wrong architectural decision in a financial system used by millions of underserved Indonesians is far too high to rush.

---

*Review submitted by Backend Engineer*
*23 June 2026*
