# Round 4: QA Final Verdict — Consensus Check

**Role:** QA Engineer  
**Subject:** TAUT Proposal v2 — Final QA Sign-Off  
**Date:** 23 Juni 2026

---

## Decision: [AGREE]

**QA approves proceeding to technical execution.**

---

## Blocker Resolution Assessment

### 🔴 BLOCKER 1: Sync Protocol Specification → ✅ RESOLVED

My original review (§8) demanded a sync protocol before we could proceed. Proposal v2 delivers a comprehensive Sync Architecture (§5.2) that actually *exceeds* my requirements:

| What I Asked For | What v2 Delivers | Verdict |
|---|---|---|
| Conflict resolution strategy | Per-entity strategy: first-write-wins (transactions), server-authoritative (prices), LWW field-level merge (user profiles), CRDT (points/counters) | ✅ Better than generic LWW — entity-aware resolution |
| Sync scheduling | Event-triggered + WorkManager (15min WiFi / 4h cellular) | ✅ Matches recommendation |
| Data format | gRPC bidirectional stream + Protobuf (<50KB/round, delta-only) | ✅ Protobuf adopted as recommended |
| Ordering mechanism | Lamport clocks (server-assigned monotonic versions); device timestamps advisory only | ✅ Robust against clock skew |
| Offline price snapshots | Price snapshot bundled with each offline transaction | ✅ Prevents price-change-during-offline error |
| Error recovery | Exponential backoff retry; per-transaction status (✅ synced / 🔄 pending / ❌ failed) | ✅ Clear state machine |
| Anti-ghost guarantee | UUIDv7 local assignment; server rejects duplicates | ✅ Deterministic, offline-safe |

**Key quality gate:** Proposer confirms (F1) that the sync architecture will be a *formal design document* delivered in Phase 0, and **QA will review and approve before any sync code is written**. This is exactly the safeguard I wanted.

### 🔴 BLOCKER 2: Encryption at Rest → ✅ RESOLVED

| What I Asked For | What v2 Delivers (§5.5) | Verdict |
|---|---|---|
| SQLCipher for local DB | SQLCipher (AES-256-GCM) for Room DB — **hard requirement, not optional** | ✅ Non-negotiable, enforced at architecture level |
| Android Keystore for keys | Android Keystore for HMAC signing keys | ✅ |
| Remote wipe | Remote wipe API (admin-triggered via WhatsApp verification) + auto-wipe after 10 failed attempts | ✅ Exceeds original ask |
| App lock | PIN/biometric for operator devices | ✅ |

The 4-layer security model (Device → Transport → API → Infrastructure) is comprehensive and addresses UU PDP compliance holistically, not just as a bolt-on. Proposer response (F2) correctly identifies this as non-negotiable.

### 🔴 BLOCKER 3: Financial Reconciliation Design → ✅ RESOLVED

| What I Asked For | What v2 Delivers (§6) | Verdict |
|---|---|---|
| Exact payout calculation | Double-entry ledger from day one; points are liability accounts; every credit has a corresponding debit | ✅ Structurally correct |
| Integer math | All values in satuan rupiah (integer cents). No floats. No rounding errors. | ✅ Zero-tolerance |
| Replay prevention | UUIDv7 one-time-use nonce; server rejects duplicates; HMAC-SHA256 signed QR payloads; 7-day expiry; server stores used nonces | ✅ Multi-layered defense |
| Daily audit job | Daily auto-reconciliation: device-calculated vs server-calculated payout. Any mismatch >0 rupiah → freeze + flag + notify | ✅ Zero-tolerance policy |

The zero-tolerance reconciliation policy (any mismatch, even 1 rupiah, freezes payouts) is aggressive but *correct* for a system handling real money with vulnerable users. Trust is built on correctness.

---

## High-Priority Item Resolution

| Item | Status | Notes |
|---|---|---|
| **Offline Authentication** (F4) | ✅ Addressed (Compromise) | One-time online setup via pendamping, then fully offline PIN auth. Admin-assisted recovery for phone changes. This is practical and realistic — full offline registration would introduce unmanageable security risks for marginal benefit. |
| **Multi-Account / Device Sharing** (F5) | ✅ Addressed | Kiosk mode with 5 PIN-protected profiles, <5 second switch. Matches real bank sampah operations. |
| **QR Code Security** (F6) | ✅ Addressed | HMAC-SHA256 + UUIDv7 nonce + 7-day expiry + server-side nonce storage. Sufficient for MVP; can be hardened in Fase 2. |

---

## Performance Thresholds — Adopted Verbatim

v2 §12 adopts my performance thresholds from §7.2 of the original review with zero modifications:

- Cold start: <5s ✅
- Transaction entry: <2s ✅
- 5,000 records in local DB: <10MB increase, <500ms query ✅
- Sync payload: <500KB/day ✅
- Memory: <100MB RSS on 1GB device ✅
- Battery: <5%/hour ✅
- ANR: Zero per 100h testing ✅
- Storage after 1 year: <200MB ✅

Release gates (P0/P1/P2) are identical to my original specification. No dilution.

---

## Edge Cases — All Adopted

Every edge case from my original §3 is now in the risk register (§10):

| My Original Edge Case | v2 Risk Register Entry | Mitigation Added |
|---|---|---|
| MicroSD removal | Handled by Room/SQLCipher (internal storage default) | ✅ |
| Phone number change | Admin-assisted recovery via WhatsApp | ✅ |
| Multiple users per device | Kiosk mode with PIN profiles | ✅ |
| Battery optimization killing sync | WorkManager constraints + foreground service for active sync | ✅ |
| Zero/negative weight | Input validation (implied in financial integrity) | ✅ Needs explicit floor/ceiling during Sprint planning — minor, not a blocker |
| Duplicate QR scan | UUIDv7 one-time nonce + server rejection | ✅ |
| QR expiry | 7-day expiry, enforced server-side | ✅ |
| Category renames | UUID-based references (category_id, not name) in schema | ✅ |
| Floating-point rounding | Integer arithmetic mandate (satuan rupiah) | ✅ |
| Pengepul bankruptcy | Points are TAUT liability, not pengepul liability | ✅ |
| DLH staff turnover | RBAC lifecycle implied in access control section | ✅ Needs explicit offboarding checklist — minor, not a blocker |
| Natural disaster | App works 100% offline; server DR in Singapore (Kominfo-approved) | ✅ |
| SIM cloning / OTP interception | OTP only for initial setup; PIN for daily auth | ✅ |

---

## Minor Observations (Non-Blockers)

These are notes for Sprint 1 planning, not objections:

1. **Phase 0 is tight at 2 weeks.** Seven parallel activities (sync spike, QR PoC, legal review, API contracts, field visits, card sorting, device procurement) is feasible *only* with disciplined parallelization and zero scope creep. I recommend the PM treat Phase 0 scope as immutable — anything that doesn't finish in 2 weeks gets documented as a known gap for Sprint 1.

2. **Zero-weight and input bounds.** The edge case for sub-gram weights and physically impossible values (e.g., 9999 kg of bottles) needs an explicit input validation rule during Sprint planning: minimum weight threshold (e.g., 0.1 kg), maximum per-transaction weight (e.g., 500 kg), maximum per-household per-week. These are implementation details, not proposal-level blockers.

3. **DLH offboarding.** When a DLH staff member leaves their role, their dashboard access must be revoked. The RBAC lifecycle (onboarding → role change → offboarding) should be documented in Sprint 1. This is an operational detail, not a blocker.

4. **Memory budget consistency.** The 300MB ceiling (FE review) and 100MB RSS target (QA §7.2) are compatible — 100MB is the typical load target, 300MB is the absolute ceiling including system overhead and peak scenarios. No conflict, but worth being explicit in Sprint docs.

---

## What the Proposer Did Well

- **Accepted nearly all critiques.** 52 out of 54 reviewer items accepted, 2 reasonable compromises, only 3 rejections (cross-platform premature, Phase 0 duration, problem statement unchanged) — all defensible.
- **No ego-driven defense.** The response document (§H) clearly explains rejections with rationale, rather than dismissing critiques.
- **Synthesized across reviewers.** v2 doesn't just address individual items — it shows cross-cutting themes (e.g., the "incentive chain must start with nasabah" insight from PM that reshapes the entire phasing).
- **Embedded QA requirements.** My testing strategy, release gates, and performance thresholds were adopted verbatim. This is rare and appreciated — it means QA isn't fighting for testability after the fact.

---

## Final Verdict

**QA gives [AGREE] to proceed to technical execution.**

All three blockers from Round 2 are fully resolved. The proposal v2 is the most testable, security-conscious, and risk-aware version I could have asked for given the project constraints. The sync architecture is well-specified, encryption is non-negotiable, financial integrity is provable through integer math and zero-tolerance reconciliation, and the testing strategy with release gates provides a clear quality framework.

The remaining minor observations (input validation bounds, DLH offboarding lifecycle, Phase 0 risk) are implementation-level details that belong in Sprint planning, not in the proposal. They do not block progress.

**One final request:** Before Sprint 1 begins, I want to review and approve the formal Sync Architecture design document (promised as a Phase 0 deliverable in F1). This is the single most critical system in the platform, and I want to verify the test harness requirements are embedded in the spec before a single line of sync code is written.

**Status: APPROVED FOR TECHNICAL EXECUTION.**

---

*Prepared by: QA Engineer*  
*23 Juni 2026*
