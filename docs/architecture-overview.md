# TAUT — Architecture Overview (2-Minute Read)

*A concise summary of `docs/architecture.md` for new team members.*

---

## What is TAUT?

An offline-first Android app + backend platform for **digital waste bank operations** in Indonesia. Operators record waste transactions (weigh → categorize → save) on low-end Android Go devices (1GB RAM), sync when online, and generate reports.

---

## High-Level Architecture

```
┌─────────────┐     gRPC bidi      ┌──────────────────────────────────┐
│  Android    │ ◀─────────────────▶ │  GCP Jakarta (asia-southeast2)   │
│  (Offline-  │     Protobuf       │  ├─ Envoy API Gateway (gRPC/REST)│
│   First)    │                    │  ├─ Auth Service (JWT + OTP SMS) │
└─────────────┘                    │  ├─ Sync Engine (bidi stream)    │
                                   │  ├─ Transaction Processor        │
                                   │  │  (Redis Stream → Workers → PG) │
                                   │  ├─ Ledger Service (Double-Entry)│
                                   │  ├─ SMS Service (Twilio/Jatis)   │
                                   │  └─ Dashboard API (Materialized) │
                                   └──────────────────────────────────┘
                                            │
                                   ┌────────▼────────┐
                                   │  PostgreSQL 15  │
                                   │  + TimescaleDB  │
                                   │  (Hypertables)  │
                                   └─────────────────┘
```

---

## Key Design Decisions (TL;DR)

| Concern | Decision | Why |
|---------|----------|-----|
| **Mobile** | Kotlin Native + Compose | 3–4 MB APK, best 1GB RAM perf |
| **Local DB** | Room + SQLCipher | AES-256 at rest, WAL, WorkManager-native |
| **Sync** | gRPC bidi stream + Protobuf | Small payloads, server push, low bandwidth |
| **Ordering** | Lamport clock (server-authoritative) | No vector clocks needed — clear ownership per entity |
| **Conflicts** | First-write-wins (transactions), LWW field-merge (profiles), CRDT (points) | Immutable financial data; no lost increments |
| **Idempotency** | Client UUIDv7 + server Bloom filter | Retry is ALWAYS safe |
| **Financial** | Integer satuan rupiah (Long), double-entry ledger | No floating-point errors, audit trail |
| **Security** | 4-layer: Device (Keystore/SQLCipher) → Transport (TLS 1.3 + pinning) → API (JWT + HMAC) → Infra (VPC, WAF) | Defense in depth |
| **Privacy** | UU PDP compliant by design | Data minimization, consent logs, export/forget, Jakarta-only data |

---

## Mobile App Structure

```
app/src/main/java/com/taut/app/
├── data/
│   ├── local/        # Room entities, DAOs, SQLCipher
│   ├── remote/       # gRPC stubs, DTOs
│   ├── repository/   # Repository implementations
│   └── sync/         # SyncWorker, DeltaSyncEngine, ConflictResolver
├── domain/
│   ├── model/        # Pure Kotlin domain models
│   ├── usecase/      # Business logic (single-responsibility)
│   └── repository/   # Repository interfaces
├── ui/               # Compose screens (Home, Weigh, History, Prices, Settings)
├── di/               # Hilt modules
└── util/             # TTS, CryptoManager, NetworkMonitor
```

**Compose tree budget:** ≤ 80 nodes, max depth 5. Critical for 1GB devices.

---

## Sync Protocol (Core)

1. **WorkManager** triggers `SyncWorker` on: app open, after tx, network restored, 15min (WiFi)/4h (cellular), manual
2. **Device opens gRPC bidi stream** → sends `SyncBatch` (pending txs + lamport timestamp + cursor)
3. **Server processes** → returns `SyncResponse` (acks + server IDs + catalog/profile updates + new lamport)
4. **Device applies**: updates local status (`pending_sync` → `synced`/`failed`), merges server data, schedules next sync

**Payload budget:** ~332 bytes/tx → 50 tx batch = ~17 KB. Well under 50 KB limit.

---

## Offline Transaction States

```
Created → pending_sync → syncing → synced → confirmed
                    ↓
                 failed → (retry backoff: 30s, 2m, 10m, 1h) → failed_manual
```

**Anti-ghost guarantee:** 100% of offline transactions reach server within **5 minutes** of connectivity restore.

---

## Financial Ledger (Double-Entry)

Every waste transaction creates:
```
DEBIT  Revenue (Commission)     = commission_amount  [Fase 2+]
CREDIT Customer Points (Liability) = points_earned    [Fase 1+]
```

- **All values in satuan rupiah (Long)** — no floats anywhere
- Chart of accounts: 1000 Assets, 2000 Liabilities, 4000 Revenue, 5000 Expense

---

## Performance Budgets (1GB Device Target)

| Component | Budget |
|-----------|--------|
| Android OS + Services | ~100 MB |
| TAUT App (Compose + Room) | ~60 MB |
| SQLCipher WAL cache | ~20 MB |
| gRPC / OkHttp | ~15 MB |
| Coil (no disk cache) | 0 MB |
| Temp allocations | ~25 MB |
| **Safety margin** | **~80 MB** |
| **Total peak** | **~300 MB** (hard ceiling) |

---

## Where to Go Next

| Document | For |
|----------|-----|
| `docs/architecture.md` | Full technical spec (all 9 sections) |
| `docs/infrastructure.md` | Cloud setup, GKE, CI/CD, DR |
| `docs/api-spec.md` | Protobuf definitions, gRPC/REST endpoints |
| `docs/database-schema.md` | SQL DDL, indexes, partitioning strategy |

---

*Generated from architecture.md v2.0 (Fase 0 — Architecture Spike). Keep this file in sync when the full doc changes.*
