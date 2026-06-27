# Round 3: Proposal v2 — REVISED

**Project:** TAUT — Platform Daur Ulang Digital
**Version:** 2.0 (Incorporating feedback from PM, Architect, UX, FE, BE, QA)
**Date:** 23 Juni 2026

---

## Executive Summary (What Changed)

v1 was a vision document. v2 is an execution plan shaped by six critical reviews. Key changes:

| Dimension | v1 | v2 |
|-----------|----|----|
| **MVP Scope** | 8 features (overloaded) | 4 features (lean, testable) |
| **Tech Stack** | Not specified | **Locked:** Kotlin Native + Jetpack Compose + Room + WorkManager + Hilt DI |
| **Phasing** | Fase 1→2→3 | **Fase 0→1→2→3** (incentive-first ordering) |
| **QR Receipt** | Primary mechanism | **SMS is default**; QR is optional/premium |
| **QRIS** | Phase 1 value prop | **Phase 2** (after legal review + PSP partnership) |
| **Camera Scanning** | Assumed needed | **Deferred to Fase 2** |
| **Sync Architecture** | "Periodic sync" (1 sentence) | **Full architecture** with gRPC, delta sync, Lamport clocks |
| **Infrastructure** | Missing | **Workload estimates + deployment topology + $2K–$6K/mo budget** |
| **UU PDP** | Missing | **Full compliance plan** + security architecture |
| **Financial Ledger** | Missing | **Double-entry from day one** (integer math, no floats) |
| **User Acquisition** | 2 sentences | **Dedicated GTM section** with named cities and targets |
| **Revenue Model** | 0.5–1% commission (naive) | **3-tier:** CSR → Data-as-Service → Premium |
| **Accessibility** | Missing | **TAUT-specific Accessibility Checklist** built into requirements |
| **Shared Device** | Not addressed | **Kiosk mode** with PIN-secured operator profiles |
| **Device Matrix** | Vague "HP Rp500rb" | **5 specific phone models** tested before Sprint 1 |
| **Memory Budget** | Missing | **300MB ceiling**, per-component allocation |
| **Exit Criteria** | Missing | **Defined go/no-go metrics** after MVP |

---

## 1. Problem Statement (Unchanged — Validated by All Reviewers)

Indonesia menghadapi **krisis sampah yang semakin parah**, diperburuk oleh fragmentasi ekosistem daur ulang informal yang menjadi tulang punggung pengelolaan sampah nasional.

### Fakta Lapangan (2026):

- Indonesia menghasilkan **±70 juta ton sampah per tahun**; ~40% tidak terkelola — dibakar, dibuang ke sungai, atau berakhir di laut.
- **Bank Sampah** di **11.000+ lokasi** nasional, >80% masih menggunakan **buku catatan manual** — data tidak terpusat, tidak bisa diaudit.
- **3–4 juta pemulung** bekerja tanpa transparansi harga, tanpa akses ke sistem keuangan formal.
- **Pemerintah daerah** tidak memiliki data real-time tentang volume sampah yang didaur ulang vs. dibuang ke TPA.
- **Tekanan 2026:** Larangan impor sampah plastik, UU Pengelolaan Sampah, Perpres Kebijakan Kelautan, dan tekanan ESG dari investor/eksportir.

### Akar Masalah
Bukan kurangnya kesadaran — tetapi **tidak adanya infrastruktur digital yang menghubungkan seluruh rantai daur ulang** dengan cara yang sederhana, offline-friendly, dan menguntungkan semua pihak.

---

## 2. Target Users (Refined — Incentive Chain Explicit)

| Tier | User | Jumlah | Pain Point | Immediate Value from TAUT |
|------|------|--------|------------|--------------------------|
| **P1** | **Operator Bank Sampah** (RT/RW, kelurahan, usia 40–55, literasi digital rendah) | 50.000+ unit | Catatan manual, rekap bulanan 3+ jam, laporan ke DLH selalu telat | **Auto-generated monthly report (5 menit vs 3 jam)** — the operator's personal time savings |
| **P2** | **Pemulung / Nasabah Rumah Tangga** | 3–4 juta orang / 5–10 juta KK | Tidak ada bukti setor, tidak ada insentif, tidak tahu harga | **SMS receipt + poin digital** (bisa ditukar pulsa/sembako) — tangible reward |
| **S1** | **Pengepul Besar / Aggregator** | Ratusan per kota | Supply tidak konsisten, kualitas tidak terjamin | **Exclusive access to supply data** + early adopter benefits |
| **S2** | **DLH / Pemerintah Daerah** | 514 kabupaten/kota | Tidak punya data terpercaya | **Data agregat real-time** (setelah network mencapai critical mass) |

**Key insight from PM review:** The person who does the work (operator) must see immediate personal value. The "auto report" feature is that value — not "better data for pemda."

---

## 3. Solution Overview

**TAUT** adalah **platform Android offline-first** yang mendigitalkan pencatatan dan transaksi di bank sampah — dengan transparansi harga, bukti setor digital, dan insentih poin untuk nasabah.

### Tiga Lapisan Inti:

**Lapisan 1: Catat** (Digital Ledger untuk Bank Sampah)
- Android app <15MB, offline-first, works on HP Rp500rb (1–2GB RAM)
- Gantikan buku catatan: timbang → pilih kategori (foto real, bukan ikon) → input berat → otomatis tercatat
- SMS receipt ke nasabah sebagai bukti setor (NO app required on nasabah side)
- Audio confirmation setelah setiap transaksi

**Lapisan 2: Harga** (Market Reference Pricing — Fase 1+)
- Harga acuan untuk 50+ kategori sampah anorganik
- Static/manual pricing untuk MVP; real-time dari pengepul di Fase 2

**Lapisan 3: Tumbuh** (Insentif & Data — Fase 1+)
- Poin digital untuk nasabah (tukar pulsa/sembako dulu, QRIS nanti)
- Operator dashboard: volume, tren, laporan otomatis
- DLH dashboard: data agregat per kecamatan (Fase 2)

---

## 4. Phasing (Revised — Incentive-First Ordering)

### Fase 0: REAL MVP (Month 1–3) — "Apakah ini menghemat waktu operator?"

**The single question this phase answers:** "Does using TAUT save the operator time compared to their buku catatan?"
**Exit criteria:** After 3 months with 20 bank sampah → if <30% are active weekly users → pivot or kill.

**Non-negotiable MVP scope:**

| Feature | Description | Priority |
|---------|-------------|----------|
| **Catat App (Offline-First)** | Android app: timbang → pilih kategori (foto real) → input berat → simpan. Works 100% offline. <15MB APK. | 🔴 BLOCKER |
| **SMS Receipt** | After each transaction, SMS sent to nasabah (queue if offline, send on sync). Contains: waste type, weight, value, transaction ID, running balance. No app needed on nasabah side. | 🔴 BLOCKER |
| **Basic Operator Dashboard (Web)** | Total volume per hari/minggu/bulan, per kategori. Auto-generated monthly report (PDF, ready for DLH). | 🔴 BLOCKER |
| **Auth via Phone + PIN** | SMS OTP for initial setup. PIN (bcrypt, verified locally) for daily use. Kiosk mode: up to 5 operator profiles per device, PIN-protected. | 🔴 BLOCKER |
| **Audio Feedback** | TTS confirmation after each transaction ("Tersimpan: 5 kg kardus, Rp 7.500"). Works offline. Indonesian voice. | 🔴 BLOCKER |
| **Sync Engine** | Background sync via WorkManager. Delta-only sync. Per-transaction status (✅ synced / 🔄 pending / ❌ failed). | 🔴 BLOCKER |
| **Encryption at Rest** | SQLCipher (AES-256-GCM) for Room DB. Android Keystore for HMAC keys. | 🔴 BLOCKER |

**Explicitly NOT in MVP (deferred):**
- ❌ Harga acuan real-time (static/manual is fine)
- ❌ Poin system (Fase 1)
- ❌ Camera QR scanning (Fase 2)
- ❌ QRIS/e-money payout (Fase 2)
- ❌ DLH dashboard (Fase 2)
- ❌ Pickup requests (Fase 3)
- ❌ Pengepul/pabrik onboarding tools (Fase 3)

**MVP delivery:** 3 months. 20 bank sampah across 3 cities. 30%+ weekly active user target.

**Phase 0 (pre-Sprint, 2 weeks): Architecture Spike**
1. Sync protocol prototype (gRPC bidir stream, delta sync, Lamport clocks)
2. QR code signing proof-of-concept (HMAC + Android Keystore)
3. UU PDP legal review (specific compliance requirements)
4. Buy target phones (5 models), build and test 3-screen PoC
5. Field visits to 5–10 bank sampah, create As-Is journey map
6. Card sorting study with 10–15 operators for category icons
7. API contracts (OpenAPI spec for core endpoints)

---

### Fase 1 (Month 3–5): Insentif untuk Nasabah — The Viral Loop

**Value proposition:** Nasabah tell neighbors "I got free pulsa for my botol plastik."

**Scope:**
- **Poin digital** — setiap setor sampah, nasabah dapat poin
- **Redemption sederhana** — tukar poin ke pulsa, paket data, atau sembako (mitra lokal)
- **No QRIS yet** — payout via mitra fisik (sembako) + pulsa (via API operator seluler)
- **Harga acuan statis** — mulai 20 kategori utama, manual update via dashboard admin
- **Anti-fraud dasar** — max X kg per household per week; weight photo required above threshold (10kg)

---

### Fase 2 (Month 5–8): Monetisasi & Skalabilitas

**Scope:**
- **QRIS/e-money payout** — partner with ONE PSP (Midtrans/Xendit), legal review for PJP exemption
- **Harga real-time** dari pengepul mitra (push via gRPC server-stream)
- **Dashboard DLH** — data agregat per kecamatan, query via pre-computed materialized views
- **Camera QR scanning** untuk nasabah yang punya app (optional)
- **Financial reconciliation** — daily auto-reconciliation job, integer math, audit trail
- **Pengepul onboarding** — pengepul bisa pasang harga beli, lihat supply availability

---

### Fase 3 (Month 8–12): Jaringan Penuh & ESG

**Scope:**
- **Pickup request** — rumah tangga bisa request pickup via SMS/app
- **Pabrik daur ulang onboarding** — direct traceability from source to recycler
- **ESG reporting tools** — auditable data for corporate partners (Danone, Unilever, etc.)
- **Premium tier for pengepul** — advanced analytics, supply quality tracking, priority listing
- **Data-as-Service for DLH** — kabupaten/kota subscription (Rp50–100 juta/tahun)

---

## 5. Technical Architecture (New — Fully Specified)

### 5.1 Tech Stack (LOCKED)

| Layer | Technology | Rationale |
|-------|-----------|-----------|
| **Mobile App** | Kotlin Native + Jetpack Compose | Smallest APK (~3–4MB baseline), best 1GB RAM perf, native WorkManager/Room/CameraX access |
| **UI Framework** | Compose for data screens; View (XML) for camera (Fase 2) | Memory constraints on 1GB devices |
| **Local DB** | Room (SQLite + SQLCipher) | Jetpack standard, best WorkManager integration, encryption at rest |
| **Sync** | gRPC bi-directional stream + Protobuf | Smaller payloads than REST/JSON, stream support, efficient for low-bandwidth |
| **DI** | Hilt | Standard Android DI, compile-time safety |
| **Image Loading** | Coil | Kotlin-native, coroutine-based, smallest APK impact (~200KB) |
| **QR** | ZXing (Android fork, ~50KB) | Most battle-tested; lightweight fork available |
| **TTS** | Android TTS (offline) | Free, works offline, Indonesian voice available |
| **Min API** | 26 (Android 8.0) | Covers 95%+ of Indonesian devices |
| **Target Devices** | Android Go (2GB RAM, 480p+ screen) | Redmi A2, Samsung A05, Advan G5, Evercoss A66, Nokia C1 |

**Rejected:** Flutter (APK baseline 7MB, RAM overhead 30MB+), React Native (Hermes baseline 8–12MB, unreliable background sync)

---

### 5.2 Sync Architecture (THE Critical System)

**Protocol:** gRPC bidirectional stream with Protobuf serialization
**Cadence:** Event-triggered (app open, after transaction, on network reconnect) + scheduled (WorkManager: 15min on WiFi, 4h on cellular)
**Payload limit:** <50KB per sync round (delta-only)

**Conflict Resolution by Entity Type:**

| Entity | Strategy | Detail |
|--------|----------|--------|
| **Transactions** | First-write-wins, append-only | Never edit finalized transactions; use compensating entries. Server rejects duplicate UUIDv7. |
| **Price Catalogs** | Server-authoritative | Device always accepts server's latest version on sync. Price snapshot bundled with each offline transaction. |
| **User Profiles** | Last-write-wins, field-level merge | Server clock as authority. Device clock is advisory only. |
| **Points/Inventory** | CRDT (additive counters) | Only increments/decrements; never overwrites absolute values. |

**Ordering Mechanism:** Lamport clocks (server-assigned monotonically increasing version numbers). Device timestamps are captured but used only for display and drift estimation — never for ordering.

**Local Transaction States:**
```
pending_sync → syncing → synced → confirmed
    ↓ (on failure)
  failed (retry with exponential backoff)
```

**Anti-ghost guarantee:** Every transaction gets a UUIDv7 locally before sync. Server rejects duplicate UUIDs. 100% of offline transactions MUST arrive at server within 5 minutes of connectivity restore.

---

### 5.3 Database Schema (Adopted from Architect Review)

**Key tables:**
- `waste_banks` — id (UUIDv7 PK), code (BSA-XXXX), name, phone, village_id, device_pub_key
- `waste_categories` — id (UUIDv7 PK), code, name_id, category_group, unit_price, is_active
- `transactions` — id (UUIDv7 PK), bank_sampah_id, operator_id, customer_id, transaction_type, status, total_weight, total_value, device_timestamp, server_timestamp, sync_id, is_offline_created
- `transaction_items` — id (UUIDv7 PK), transaction_id, category_id, weight, price_per_unit (LOCKED at transaction time), total_value
- `users` — id (UUIDv7 PK), phone_number (UNIQUE), role, name, location_id, kyc_status, pin_hash
- `ledger_entries` — id (UUIDv7 PK), account_id, entry_type (credit/debit), amount, reference_type, reference_id, balance_after (running balance for audit)
- `price_references` — id (UUIDv7 PK), category_id, region_id, source, price_per_unit, effective_from, effective_to, version
- `qr_codes` — id (UUIDv7 PK), transaction_id, qr_content (JSON payload), qr_version, printed_at, verified_at
- `sync_log` — id (UUIDv7 PK), device_id, last_sync_cursor, records_synced, records_failed

**Key decisions:**
- UUIDv7 for all PKs (time-ordered, collision-free, works offline)
- TimescaleDB hypertable for `transactions` and `price_history`
- Monthly partitioning for `ledger_entries`
- Integer arithmetic for all financial values (satuan rupiah — no floating point)

---

### 5.4 Infrastructure (Phase 1)

**Cloud:** GCP asia-southeast2 (Jakarta region) — mandatory for data residency
**DR:** Singapore (asia-southeast1) — cross-region replication approved by Kominfo

**Component breakdown:**

| Component | Detail | Est. Cost/Month |
|-----------|--------|-----------------|
| **Compute** | Auto-scaling k8s (GKE), 4–16 nodes burst | $800–$2,500 |
| **Database** | PostgreSQL 15 + TimescaleDB, 500GB SSD, PITR | $400–$1,200 |
| **Cache** | Redis 7+ (Memorystore), 10GB | $150–$400 |
| **CDN** | Cloudflare (static assets, QR redirects) | $200–$500 |
| **Storage** | GCS for audit logs, backups | $100–$300 |
| **SMS Gateway** | OTP + transaction receipts (~45K SMS/month) | $200–$500 |
| **Monitoring** | Grafana + Loki + Mimir | $100–$300 |
| **Total Phase 1** | | **~$2,000–$6,000/month** |

**Sync spike handling:** API Gateway with token bucket rate limiting (100 req/min per device). Message queue (Redis Streams) for transaction ingestion. Worker pool for async processing. Horizontal scaling with PgBouncer connection pooling.

---

### 5.5 Security & UU PDP Compliance

**4-Layer Security Model:**

| Layer | Component | Implementation |
|-------|-----------|----------------|
| **L1: Device** | Local data encryption | SQLCipher (AES-256-GCM), Android Keystore for HMAC keys |
| | App lock | PIN/biometric for operator devices |
| | Remote wipe | Triggered by admin call/WhatsApp verification |
| | Auto-wipe | After 10 failed unlock attempts |
| **L2: Transport** | TLS 1.3 | Mandatory, no downgrade, certificate pinning |
| **L3: API** | JWT auth | 15-min expiry + refresh rotation |
| | Rate limiting | 100 req/min per device |
| | Request signing | HMAC-SHA256 for sync payloads |
| | Audit logging | All data access logged |
| **L4: Infrastructure** | Network | VPC with private subnets for databases |
| | WAF | Cloudflare/AWS WAF for SQLi/XSS |
| | Secrets | Vault/AWS Secrets Manager |
| | Pen testing | Quarterly |

**UU PDP Compliance Map:**

| UU PDP Article | Requirement | Implementation |
|----------------|-------------|----------------|
| **Pasal 16** | Data minimization | Collect only: phone number, name, location (kelurahan). No GPS, contacts, SMS, IMEI, photos (optional). |
| **Pasal 20** | Consent | Explicit consent screen for: (a) recording transactions, (b) sharing aggregate data with DLH. Separate from auth. |
| **Pasal 26** | Data subject rights | `/export` endpoint for data portability. Deletion API for right-to-be-forgotten. |
| **Pasal 30** | Breach notification | 3×24 hour notification protocol. Incident response plan documented. |
| **Pasal 46** | Cross-border transfer | All production data in Indonesia (GCP asia-southeast2). DR in Singapore (Kominfo-approved). |
| **Pasal 57** | DPO | Appoint Data Protection Officer by Fase 1 launch. |

---

## 6. Financial System Design (New)

### 6.1 Financial Architecture Principles

1. **Double-entry ledger from day one** — Even before QRIS. Points are a liability account. Every credit has a corresponding debit somewhere.
2. **Integer arithmetic only** — All values in satuan rupiah (cents). No floats. No rounding errors.
3. **Idempotency keys** — Every mutation endpoint requires a client-generated UUID. Server checks before processing.
4. **Immutable audit log** — No updates, only appends. Every financial event has: actor, timestamp, old/new values, reason code.

### 6.2 Points System (Fase 1)

- **Earning:** Household deposits waste → points credited to their phone number (no app required)
- **Redemption (Fase 1):** Physical goods (sembako) via local partners. Pulsa/paket data via operator API.
- **Redemption (Fase 2):** QRIS payout via PSP partner (Midtrans/Xendit)
- **Anti-fraud:** Max deposit per household per week. Statistical anomaly detection (flag operators with weight/volume ratios far above regional average). Weight photo required above 10kg threshold.

### 6.3 Reconciliation (Fase 2+)

- **Daily auto-reconciliation job:** Compare device-calculated payout vs server-calculated payout
- **Zero-tolerance:** Any mismatch > 0 rupiah → freeze all payouts for affected bank sampah → flag for manual review → notify operator and nasabah
- **Audit trail:** Every financial event logged in immutable `ledger_entries` table

---

## 7. UX Design Principles (New — User-Centered)

### 7.1 Design for Ibu PKK (Age 40–55, Low Digital Literacy)

**The core principle:** *Every digital action must map to an existing physical action from paper-ledger life.*

**Layout: "Digital Notebook"**
- Columns: tanggal | nama | jenis | berat | harga | total — exactly like the paper buku
- Scrollable table (not modern card-based form)
- "Tambah Baris" button = like writing a new row in the buku
- **One screen, one action** — max 3 steps per primary action

**Navigation:**
- Home screen: 4 large tiles (80x80dp minimum)
  - [⚖️ Timbang] Hijau — primary action
  - [💰 Harga] Biru — information
  - [📋 Riwayat] Kuning — history
  - [⚙️ Atur] Abu — settings
- NO hamburger menu. NO toolbar icons. NO swipe gestures.
- Icon + text ALWAYS paired (never icon-only)

**Waste Categories:**
- **Photo-based** — real photos of actual items, not vector illustrations
- Operator recognizes "Aqua botol 600ml" not a generic "PET bottle" icon
- Card sorting study with 10–15 operators to validate categories

### 7.2 SMS Receipt Flow (Default — Replaces QR)

1. Nasabah brings waste → operator weighs + categorizes in app
2. App confirms: "5 kg Kardus = Rp 7.500" → audio TTS confirmation
3. Operator taps [✅ Simpan]
4. SMS sent to nasabah's phone: *"Setor 5kg Kardus di Bank Sampah Melati. Nilai: Rp7.500. No: T-2306-001. Saldo: Rp45.000"*
5. If offline → SMS queued, sent on sync. Transaction number given verbally/n written on slip.

**Why SMS (not QR or app):** 95%+ phone penetration in Indonesia. Even Nokia feature phones receive SMS. No app, no internet, no scan needed. This is the most inclusive mechanism.

### 7.3 Accessibility Checklist (TAUT-Specific)

| Category | Requirement | Standard |
|----------|-------------|----------|
| **Visual** | Minimum contrast ratio | 4.5:1 (WCAG AA) |
| | Font size | 18sp body, 24sp title |
| | High contrast | DEFAULT theme (not optional toggle) |
| | Color + icon redundancy | Never use color alone to convey info |
| | Dark mode | Default for outdoor use |
| **Motor** | Touch target | 56x56dp minimum (exceeds 48dp WCAG) |
| | Gestures | Tap and scroll only. No swipe, pinch, long-press |
| | Destructive actions | Confirmation dialog with clear text (no undo toast) |
| **Cognitive** | Steps per action | Maximum 3 |
| | Pop-ups/modals | Zero — all information inline |
| | Language | Bahasa Indonesia sederhana, zero jargon |
| | Progress indicator | "1/3: Timbang, 2/3: Pilih kategori, 3/3: Selesai ✅" |
| **Offline** | All core features | 100% functional offline |
| | Error states | Never blank or broken when offline — always functional cached state |

### 7.4 Onboarding (Concrete, Not Vague)

**First Run Experience (<2 minutes):**
1. Open app → illustrated welcome screen (no text) → "Tekan Mulai"
2. Enter phone number → SMS OTP (auto-read if supported)
3. Set PIN (4 digits, entered twice)
4. See home screen with 4 tiles → "Tekan [⚖️ Timbang] untuk mulai"

**Human support:**
- **TAUT Champions** — 5–10 local tech-savvy pendamping (mahasiswa, fresh grad) stationed in target cities
- Each pendamping spends 1–2 weeks per bank sampah: (a) data migration, (b) training, (c) first-week hand-holding
- WhatsApp group regional for peer support (operator-to-operator, not support-to-operator)

**Transition strategy:**
- Week 1: Operator uses buku AND app (dual running)
- Week 2: Operator uses app, buku as backup
- Week 3+: App primary, buku retired
- Key principle: operator never fears data loss because buku still exists during transition

---

## 8. User Acquisition & Go-to-Market (New — Concrete)

### 8.1 Target Cities (Phase 0 — Pre-MVP)

| City | Rationale | Target Banks | Anchor Pengepul Candidate |
|------|-----------|-------------|--------------------------|
| **Bandung** | Strong bank sampah network (ASOBSI West Java), tech-savvy population, close to Jakarta for team | 10 banks | PT XYZ (large aggregator in Bandung) |
| **Surabaya** | Active DLH, existing digital initiatives, port city with recycling industry | 5 banks | TBD — field research needed |
| **Makassar** | Eastern Indonesia representation, growing waste management ecosystem, different economic profile | 5 banks | TBD — field research needed |

**Total Phase 0 target:** 20 bank sampah across 3 cities.

### 8.2 Acquisition Channels

1. **ASOBSI Partnership** — Asosiasi Bank Sampah Indonesia. Find digitally-literate early adopters (~500 banks already using WhatsApp for coordination).
2. **Pengepul as Trojan Horse** — Negotiate with 1 major pengepul per target city: "We only buy from TAUT-connected banks" → adoption becomes mandatory.
3. **DLH Recommendation** — Get DLH to "recommend" TAUT (not mandate). Offer incentives (recognition, small grants) for early adopters.
4. **Pendamping Model** — TAUT Champions do door-to-door onboarding at bank sampah locations.

### 8.3 Realistic 6-Month Target

- **Month 1–3:** 20 bank sampah (deep pilot, learn, iterate)
- **Month 4–6:** 200–500 bank sampah (expand within 3 cities + 1 new city)
- **Not 1,000+** — quality over quantity. Deep adoption first.

### 8.4 KPIs

| Metric | Target | When |
|--------|--------|------|
| Weekly active users (of pilot banks) | >30% | Month 3 |
| Transactions per bank per day (active) | >10 | Month 3 |
| NPS (operator survey) | >30 | Month 3 |
| SMS receipts delivered | >95% | Month 3 |
| Data sync success rate | >99.5% | Month 3 |

---

## 9. Dashboard & Data Architecture

### 9.1 Operator Dashboard (MVP — Web)
- Total volume per hari/minggu/bulan (chart)
- Breakdown per kategori sampah
- Auto-generated monthly report (PDF) — ready to submit to DLH
- **Purpose:** Save operator 3 hours of manual rekap each month

### 9.2 DLH Dashboard (Fase 2 — Web)
- Total tonnage deposited per kecamatan (daily)
- Waste composition breakdown (weekly)
- Number of active bank sampah (monthly)
- Recycling rate (diverted from TPA, monthly)

**Data Architecture (Critical):**
- ❌ **NEVER** query transaction tables directly for dashboard
- ✅ **OLAP aggregation layer**: Materialized views refreshed hourly
- ✅ Pre-computed daily/weekly/monthly rollups by region × category
- ✅ Separate analytics database (TimescaleDB hypertables or read replica)
- ✅ Scheduled CSV/PDF export for DLH staff who need files, not dashboards
- ✅ Bulk data API for integration with SIPSN (Sistem Informasi Pengelolaan Sampah Nasional)

### 9.3 DLH Access Control
- Read-only access scoped to user's kabupaten/kota
- API keys + OAuth2 for automated integrations
- Audit log for all data access (politically sensitive)

---

## 10. Risk Register (Revised & Expanded)

### Technical Risks

| Risk | Severity | Mitigation | Added in v2? |
|------|----------|------------|-------------|
| Offline sync conflicts (data loss/duplication) | 🔴 CRITICAL | Lamport clocks, delta sync, idempotent retry, per-transaction status | ✅ New |
| OOM on 1GB devices during camera+sync+UI | 🔴 CRITICAL | 300MB memory budget, release camera immediately, defer camera to Fase 2 | ✅ New |
| Device clock skew breaks transaction ordering | 🔴 HIGH | Lamport clocks (server-authoritative), device timestamp is advisory only | ✅ New |
| APK exceeds 15MB | 🟡 HIGH | Kotlin native baseline 3-4MB; aggressive R8/ProGuard; per-feature size tracking in CI | ✅ New |
| eMMC slow I/O on budget phones | 🟡 MEDIUM | Room WAL mode, batch writes, periodic cleanup of synced records | ✅ New |
| SMS costs at scale (50K MAU) | 🟡 MEDIUM | Use PIN for returning users (local auth); SMS OTP only for initial setup + recovery | ✅ New |
| Camera scan fails on fixed-focus phones | 🟡 MEDIUM | Deprioritized to Fase 2. Manual entry fallback. | ✅ New |

### Product Risks

| Risk | Severity | Mitigation | Added in v2? |
|------|----------|------------|-------------|
| Operators don't adopt (no incentive to switch) | 🔴 CRITICAL | Auto-report saves 3h/month. Poin system (Fase 1) drives nasabah demand. TAUT Champions for hand-holding. | ✅ New |
| Chicken-and-egg (single bank on TAUT is useless) | 🔴 CRITICAL | Start in 3 cities only. Pengepul anchor partner per city. First 6 months: deep adoption > wide coverage. | ✅ New |
| Shared-device failure (one HP, multiple operators) | 🔴 HIGH | Kiosk mode with PIN-secured profile switching. Up to 5 operators per device. | ✅ New |
| Fraud/gaming the poin system | 🔴 HIGH | Max kg/household/week. Weight photo above threshold. Statistical anomaly detection. | ✅ New |
| Pengepul cartel resistance | 🔴 HIGH | Phased onboarding. Exclusive supply data access as incentive. Start with smaller/independent pengepul. | ✅ New |
| Pengepul bankruptcy (affects pending points) | 🟡 MEDIUM | Points are TAUT liability (not pengepul). If pengepul drops out, points remain valid. | ✅ New |
| Power/battery — sudden shutdown mid-transaction | 🟡 MEDIUM | Transaction saved on each step (not only on final save). SQLite WAL prevents corruption. | ✅ New |
| Key-person dependency (one operator knows system) | 🟡 MEDIUM | Training redundancy: pendamping trains 2 operators per bank sampah. | ✅ New |

### Regulatory Risks

| Risk | Severity | Mitigation | Added in v2? |
|------|----------|------------|-------------|
| UU PDP breach (lost device exposes PII) | 🔴 HIGH | SQLCipher encryption. Remote wipe API. Auto-wipe after 10 failed attempts. | ✅ New |
| QRIS regulatory license (PJP from Bank Indonesia) | 🔴 HIGH | QRIS in Fase 2 (not MVP). Partnership with licensed PSP. Legal review before any financial processing. | ✅ New |
| Cross-border data transfer (Pasal 46) | 🟡 MEDIUM | GCP asia-southeast2 (Jakarta). DR in Singapore (Kominfo-approved). | ✅ New |
| SIM swap / OTP interception | 🟡 MEDIUM | SMS OTP used only for initial setup. PIN (locally verified) for daily auth. Admin-assisted recovery for phone changes. | ✅ New |

### Competition Risks

| Risk | Severity | Mitigation |
|------|----------|------------|
| Gojek/Grab/Shopee adds waste collection | 🟡 MEDIUM | Moat: offline-first + deep waste sector domain knowledge. Super-apps don't work on Rp500k phones without data. |
| Other bank sampah apps emerge | 🟡 LOW | First-mover advantage in 3 cities. Network effects once pengepul + bank sampah ecosystem is connected. |

---

## 11. Revenue Model (Revised — Three-Tier)

| Tier | Timeframe | Source | Est. Revenue | Confidence |
|------|-----------|--------|-------------|------------|
| **Tier 1: Survival** | Year 1–2 | CSR/Grants: Danone (Aqua), Unilever, Nestlé plastic waste programs. UNDP, World Bank (ocean plastic). ADB. | $100K–$500K/year | Medium — grant writing takes 6–12 months; budget effort explicitly |
| **Tier 2: Core** | Year 2–3 | Data-as-Service to pemda: Rp50–100 juta/tahun per kabupaten/kota. Target: 10–20 kabupaten by end of Year 2. | $100K–$400K/year | Medium-High — pemda need this data for regulatory compliance |
| **Tier 3: Growth** | Year 3+ | Premium features for pengepul (advanced analytics, priority listing). Commission on mature B2B volume (0.5% when volume justifies it). | TBD | Low — depends on network maturity |

**Note on Tier 1:** Grant writing is a non-trivial effort. We need a dedicated partnership/grant manager role from Month 1. The product cannot survive on grants alone — Tier 2 must be the transition to sustainability.

---

## 12. QA & Testing Strategy

### 12.1 Blocker Test Cases (Must Pass Before Any Release)

| Gate | Criteria |
|------|----------|
| **P0 — Financial Integrity** | Zero mismatches in reconciliation test (10,000 transactions, offline → sync → reconcile) |
| **P0 — Offline Sync** | All 8 sync scenarios pass (single device offline, dual-device conflict, partial sync, clock skew, simultaneous online+offline, large backlog, data corruption, concurrent sync of same account) |
| **P0 — No Data Loss** | Monkey test for 24h; any crash = BLOCKER |
| **P1 — Privacy** | UU PDP checklist fully passes; no PII in logs or crash reports |
| **P1 — Performance** | Cold start <5s; no ANR; memory <100MB RSS on 1GB device |
| **P2 — Security** | TLS verified; local DB encrypted (SQLCipher); QR payload non-replayable |
| **P2 — Accessibility** | App usable with TalkBack; minimum touch target 56x56dp |

### 12.2 Test Pyramid (Offline-First Mobile)

| Layer | % of Tests | Tool | Focus |
|-------|-----------|------|-------|
| **Unit tests** | 40% | JUnit 5 + MockK | ViewModels, price calculation, validators, data converters |
| **Service/Repository** | 25% | Room in-memory DB + Turbine | DAO queries, offline CRUD, sync queue |
| **Sync Layer** | 25% | Custom conflict simulator | All 8 conflict scenarios, network simulation |
| **E2E/Integration** | 10% | Test server + QRIS sandbox | Full flow: offline record → sync → payout |

### 12.3 Target Test Devices

| Model | Specs | Purpose |
|-------|-------|---------|
| Xiaomi Redmi A2 | 2GB RAM, Android 12 Go, 6.5" HD+ | Primary target |
| Samsung Galaxy A05 | 2GB RAM, Android 13, 6.5" LCD | Second target |
| Advan G5 | 1GB RAM, Android 10 Go, 5.5" FWVGA | Low-end extreme |
| Evercoss A66 | 1GB RAM, Android 8 Go, 5" FWVGA | Lowest end |
| Nokia C1 | 1GB RAM, Android 9 Go, 5.45" FWVGA | Brand variety |

**All 5 models will be purchased before Sprint 1.** Budget: ~Rp2.5 juta.

---

## 13. Project Timeline

| Period | Phase | Key Deliverables | Team |
|--------|-------|------------------|------|
| **Week 1–2** | **Phase 0: Architecture** | Sync protocol PoC, QR signing PoC, UU PDP legal review, API contracts (OpenAPI), field visits (5–10 banks), card sorting study, buy target phones | Arch + BE + FE + UX + PM |
| **Month 1–3** | **Fase 0: MVP Build** | Android app (Catat + SMS receipt + kiosk mode + audio feedback + offline sync), web dashboard (basic), deploy to 20 banks in 3 cities | Full team |
| **Month 3** | **MVP Pivot/Kill Gate** | Measure: active weekly users >30%? Launch or kill | PM + CIO |
| **Month 3–5** | **Fase 1: Poin** | Poin system + pulsa/sembako redemption + static price catalog + anti-fraud basics | Full team |
| **Month 5–8** | **Fase 2: Monetize** | QRIS integration (PSP partner), DLH dashboard, real-time pricing, camera scanning | Full team |
| **Month 8–12** | **Fase 3: Scale** | Pickup requests, pabrik onboarding, ESG reporting, premium tier | Full team |

---

## 14. Team & Budget (Phase 0 — First 2 Weeks)

**Phase 0 Activities (parallel):**

| Activity | Owner | Duration | Output |
|----------|-------|----------|--------|
| Sync protocol prototype | Backend + Architect | 2 weeks | Working gRPC sync demo + conflict resolution design doc |
| QR signing PoC | Frontend + Architect | 1 week | HMAC-signed QR generation + verification on target phone |
| UU PDP legal review | CIO + Legal counsel | 2 weeks | Compliance requirements document + consent flow design |
| API contracts | Backend | 2 weeks | OpenAPI spec for auth, transactions, sync, prices |
| Field visits | UX + PM | 2 weeks | As-Is journey maps from 5–10 bank sampah |
| Card sorting study | UX | 1 week | Validated waste category taxonomy + icon preference |
| Device procurement | FE + QA | 1 week | 5 target phone models purchased, basic PoC running |
| Grant/CSR research | PM | 2 weeks | List of 10+ grant opportunities + partnership contacts |

**Budget — Phase 0:** ~$15K (devices, field visit travel, legal consultation, card sorting incentives)

---

## 15. Exit/Continuation Criteria

**Go/No-Go Decision at Month 3 (after Fase 0 MVP with 20 banks):**

**GO criteria (all must pass):**
1. ≥30% of registered bank sampah are active weekly users (recording ≥5 transactions/week)
2. ≥90% of offline transactions sync successfully within 5 minutes of connectivity
3. <1% data discrepancy in transaction records (device vs server)
4. Zero financial integrity incidents (duplicate transactions, lost records)
5. Operator NPS ≥ 30 (survey conducted by pendamping, not in-app)
6. At least 1 pengepul partner committed to pilot in Fase 1

**PIVOT criteria (any 2 of):**
- Active weekly users <15%
- Data sync failure rate >10%
- Operator NPS < 0
- Zero pengepul interest

**KILL criteria:**
- All 4 pivot criteria met
- OR critical security/regulatory finding (UU PDP violation, PJP license required before MVP)

---

## 16. Out of Scope (Reaffirmed)

- **Tidak** memproses/mendaur ulang sampah secara fisik
- **Tidak** menggunakan AI/ML computer vision untuk sorting otomatis
- **Tidak** membuat marketplace B2B skala industri
- **Tidak** menangani sampah organik dan B3 pada fase awal
- **Tidak** menjadi aplikasi super-app
- **Tidak** mendukung iOS dalam 12 bulan pertama
- **Tidak** menggunakan Flutter atau React Native
- **Tidak** mengintegrasikan QRIS dalam MVP

---

## Closing

The original v1 proposal was a directional vision — designed to spark critique and improve through debate. v2 is the result: a proposal shaped by PM insight on incentives and phasing, architectural rigour on sync and security, UX empathy for low-literacy users, FE pragmatism on memory and device constraints, BE depth on financial integrity and scaling, and QA thoroughness on testability and edge cases.

**The thesis remains the same:** Indonesia's informal recycling sector needs digital infrastructure that works on the devices people actually have, with the connectivity they actually have, at the literacy level they actually have.

**What changed:** The plan for how to get there is now specific, testable, and honest about difficulty.

Let's build this.

---
*Chief Innovation Officer*
*23 Juni 2026*
