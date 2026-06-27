# Round 4: System Architect — Final Verdict

**Reviewer:** System Architect
**Date:** 23 Juni 2026
**Decision:** ✅ **AGREE TO PROCEED**

---

## Blocker-by-Blocker Assessment

I proposed 8 BLOCKERS in my Round 2 review. The Proposer accepted all 8 and the v2 proposal addresses each thoroughly. Here is my line-by-line verification:

| # | Original Blocker | v2 Status | Assessment |
|---|------------------|-----------|------------|
| 1 | **No tech stack decision** | §5.1 — Kotlin Native + Jetpack Compose + Room + WorkManager + Hilt DI. Min API 26. Android Go target. Flutter/RN explicitly rejected. | ✅ **FULLY RESOLVED.** The rationale for rejecting Flutter (7MB baseline) and React Native (8-12MB Hermes) is sound and matches my original recommendation exactly. |
| 2 | **No sync protocol** | §5.2 — Full architecture: gRPC bidirectional stream + Protobuf, delta sync (<50KB/round), Lamport clocks, entity-specific conflict resolution (append-only transactions, server-authoritative prices, CRDT counters for points, LWW for profiles). 2-week Phase 0 spike to prototype. | ✅ **FULLY RESOLVED.** This was my #1 concern. The sync architecture is now specification-grade. Conflict resolution by entity type is the correct pattern — one strategy doesn't fit all entities. The anti-ghost guarantee (UUIDv7 + server dedup) is solid. |
| 3 | **QRIS in MVP is dangerous** | Removed from MVP entirely. MVP uses offline point accumulation + physical redemption (sembako, pulsa). QRIS deferred to Fase 2 after legal review + PSP partnership. | ✅ **FULLY RESOLVED.** The Proposer correctly identified that QRIS was an architectural showstopper for offline-first design. The 3-phase QRIS phasing (offline ledger → PSP partnership → full QRIS) is pragmatic and legally safe. |
| 4 | **No infrastructure estimate** | §5.4 — GCP asia-southeast2 (Jakarta), auto-scaling k8s (4-16 nodes), PostgreSQL+TimescaleDB, Redis, Cloudflare CDN, SMS gateway, monitoring. Budget: $2K–$6K/mo Phase 1. DR in Singapore (Kominfo-approved). | ✅ **FULLY RESOLVED.** Cost estimates match my original analysis almost exactly. Data residency compliance is addressed. Sync spike handling via Redis Streams + PgBouncer is a sound approach. |
| 5 | **No UU PDP compliance plan** | §5.5 — Compliance table mapping Pasal 16 (data minimization), 20 (consent), 26 (data subject rights / export / deletion), 30 (breach notification 3×24h), 46 (cross-border transfer), 57 (DPO appointment). | ✅ **FULLY RESOLVED.** The data minimization policy (phone + name + location only; no GPS, contacts, IMEI) is the right call. The `/export` endpoint and deletion API address Pasal 26 directly. DPO appointment by Fase 1 launch is realistic. |
| 6 | **No device security model** | §5.5 — 4-layer model: L1 Device (SQLCipher AES-256-GCM, PIN/biometric, remote wipe, auto-wipe after 10 attempts), L2 Transport (TLS 1.3, certificate pinning), L3 API (JWT 15-min + refresh, HMAC signing, rate limiting, audit log), L4 Infrastructure (VPC, WAF, Vault, quarterly pen testing). | ✅ **FULLY RESOLVED.** This is production-grade security architecture. The auto-wipe after 10 failed attempts + remote wipe via admin call covers the lost-device scenario that was my primary concern. |
| 7 | **No conflict resolution strategy** | §5.2 — Entity-specific: Transactions = first-write-wins + append-only (compensating entries only); Prices = server-authoritative; User profiles = LWW field-level merge; Points = CRDT additive counters. Ordering via Lamport clocks (server-assigned). Device timestamps advisory only. | ✅ **FULLY RESOLVED.** The insight to never use device timestamps for ordering is critical and correctly implemented. The state machine (pending_sync → syncing → synced → confirmed → failed) with exponential backoff is standard for offline-first systems. |
| 8 | **No database schema** | §5.3 — 9 tables (waste_banks, waste_categories, transactions, transaction_items, users, ledger_entries, price_references, qr_codes, sync_log). UUIDv7 PKs. TimescaleDB hypertables for transactions. Monthly partitioning for ledger. Integer arithmetic (satuan rupiah). | ✅ **FULLY RESOLVED.** Schema adopted directly from my original recommendation with minor improvements (transaction_items table for multi-category transactions, balance_after field in ledger_entries for audit). |

---

## Strong Recommendations Assessment

| # | Recommendation | Status |
|---|---------------|--------|
| 9 | Target API level + device matrix | ✅ 5 specific models named, to be purchased before Sprint 1 |
| 10 | OLAP aggregation for DLH dashboard | ✅ Materialized views, pre-computed rollups, separate analytics DB |
| 11 | SMS cost planning | ✅ Included in infra costs; optimized via PIN for returning users |
| 12 | App size budget | ✅ 15MB target with per-feature CI tracking |
| 13 | CI/CD for offline-first testing | ✅ 8-scenario sync test suite, custom conflict simulator |
| 14 | UU PDP data portability | ✅ `/export` endpoint + deletion API |

All 14 items (8 blockers + 6 recommendations) from my original review are addressed.

---

## Remaining Concerns (MINOR — NOT BLOCKERS)

These are observations for the engineering team to track during execution. None are sufficient to block progress.

### 1. gRPC on Spotty Networks Needs Early Validation
The architecture spike (Phase 0, 2 weeks) must produce a working gRPC sync prototype tested on an actual budget device with simulated network interruptions. If bidirectional streaming proves unreliable on 1GB RAM devices with aggressive Android Doze, we may need to fall back to REST + server-sent events. **Mitigation is already in the plan** (Phase 0 spike). No action needed now, but this is the highest-risk technical item.

### 2. Phase 0 is Dense but Parallelizable
The Proposer's response rejects the BE reviewer's 4-week Phase 0 in favor of 2 weeks, siding with my original recommendation. I still believe 2 weeks is achievable **if parallelized correctly**: sync spike (BE + Architect), field visits (UX + PM), device procurement (FE + QA), legal review (CIO + Legal) can all run concurrently. However, the API contracts deliverable depends on sync protocol decisions — this must be sequenced after the sync spike, not parallel. Flagging this dependency.

### 3. Double-Entry Ledger Complexity in MVP
The v2 proposal adds a full double-entry financial ledger from day one (§6). This is architecturally correct (points are a liability account, every credit has a debit). However, this is non-trivial to implement correctly — especially integer arithmetic reconciliation, idempotency keys, and the immutable audit log. The engineering team must not cut corners here to hit the 3-month MVP deadline. **I recommend allocating at least 1 full sprint to the ledger subsystem.**

### 4. SMS Delivery Reliability
The proposal relies on SMS receipts as the primary proof-of-deposit mechanism. SMS delivery in Indonesia has known reliability issues (carrier filtering, delays, spam folder routing). The v2 proposal mentions "SMS queued, sent on sync" which is correct for the offline case. However, I'd like to see a **fallback for failed SMS**: the transaction ID written on a physical slip + logged in the operator's dashboard view, so the nasabah has a record even if SMS fails. The proposal hints at this but doesn't make it explicit as a requirement.

---

## Final Verdict

### **[AGREE] — Proceed to Technical Execution**

The v2 proposal is **architecturally sound and engineering-ready.** Every one of my 8 original blockers has been resolved with comprehensive, detailed solutions. The Proposer demonstrated genuine responsiveness to critique — not just surface-level patching, but deep structural improvements (reordered phasing, new architecture sections, financial system design, security layers).

**What gives me confidence:**
1. The sync architecture is specification-grade, not hand-wavy
2. QRIS has been correctly de-risked from MVP
3. UU PDP compliance is mapped to specific articles with implementation plans
4. The financial system (double-entry, integer math, idempotency) is built right from day one
5. The 2-week architecture spike before Sprint 1 is the right safeguard
6. Exit criteria with specific KPIs (>30% weekly active, >99.5% sync success, NPS >30) provide honest go/no-go gates
7. The team can now estimate, hire, and build against a defined spec

**What I'm watching during execution:**
- Phase 0 sync prototype must validate gRPC on real hardware
- Ledger subsystem must not be cut short to meet MVP deadline
- SMS delivery reliability needs monitoring and a documented fallback

This proposal went from a 2.4/5 in my original review to an **estimated 4.3/5** — a genuine, substantive improvement driven by rigorous multi-stakeholder critique. The TAUT team should proceed to Sprint 1 with confidence.

---

*System Architect • 23 Juni 2026*
