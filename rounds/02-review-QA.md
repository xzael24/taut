=== QA REVIEW ===

**Role:** QA Engineer  
**Subject:** TAUT Proposal — QA & Testing Critical Review  
**Date:** 23 Juni 2026  

---

## 1. Biggest Testing Challenges

| # | Challenge | Why It's Hard |
|---|-----------|---------------|
| 1 | **Offline-first + eventual consistency** | The proposal explicitly targets areas with unstable internet (daerah 3T). Testing sync correctness when devices come online unpredictably — with partial data, duplicate records, and stale state — is fundamentally harder than testing always-online apps. |
| 2 | **Heterogeneous device landscape** | Target phones are "Rp500 ribuan" (~US$30) Android devices. This means Android Go, 1–2 GB RAM, low storage, old OS versions (Android 8–10). Fragmentation of OS, screen size, and chipset creates a combinatorial explosion of test configurations. |
| 3 | **QRIS financial transaction accuracy** | Real money (e-money/QRIS payout) flows through the system. A bug that miscounts kilograms × price, duplicates a payout, or drops a transaction is not a UX issue — it's a financial and legal liability. Testing must prove transactional integrity under offline conditions. |
| 4 | **Multi-actor data ownership & UU PDP** | Four user types (household, bank sampah, pengepul, DLH) each have different data-access privileges. Offline sync creates a risk that data intended for one role bleeds into another's device. Testing privacy at the sync layer, not just the API layer, is novel and hard. |
| 5 | **QR code as legal receipt** | The proposal uses QR codes as "bukti setor" (proof of deposit). If the QR code system can be forged, replayed, or corrupted, the entire trust model collapses. Testing this requires cryptographic verification of offline-generated QR payloads. |

---

## 2. Offline-First Sync Testing — How to Test Properly

The proposal says "sinkronisasi data saat terhubung internet" but gives zero detail on the sync protocol. This is the riskiest technical blind spot.

### 2.1 Sync Architecture Questions the Proposer Must Answer Before We Can Test

- **Conflict resolution strategy?** Last-write-wins (LWW)? CRDT? Operational transform? Custom merge? Each has different test implications.
- **Sync trigger model?** Periodic pull? Push-based (WebSocket/FCM)? Manual button? Background job with WorkManager?
- **Delta sync or full sync?** Sending entire ledger every time is impossible on limited data plans. Tests must verify delta efficiency.
- **Ordering mechanism?** Vector clocks? Hybrid logical clocks (HLC)? Wall-clock timestamps alone will break on offline devices with incorrect clock settings.
- **Error recovery?** What happens when sync fails mid-transaction? Rollback? Idempotent retry?

### 2.2 Proposed Test Matrix for Offline Sync

| Scenario | Test Case | Expected Outcome |
|----------|-----------|------------------|
| **Single device offline** | Operator records 50 transactions offline, syncs 24h later | All 50 appear on server with correct timestamps, no duplicates |
| **Dual-device conflict** | Two bank sampah operators weigh the same bag on different devices offline, both sync later | Conflict is detected, resolved via defined strategy, audit log shows resolution |
| **Partial sync** | Device goes online mid-sync (WiFi drops) | Idempotent; next sync picks up where it left off; no double records |
| **Clock skew** | Device clock is wrong by -2 hours; user records transaction offline, then syncs | Timestamp corrected to server time OR flagged for manual review; system doesn't silently accept wrong time |
| **Simultaneous online + offline** | User starts recording offline, then internet comes back mid-transaction | Transaction completes on the side where it started; no partial state |
| **Large backlog** | 6 months of offline data (~10,000 records) syncs for first time | Progress indicator, no ANR, sync completes within reasonable time (< 5 min on 3G) |
| **Data corruption** | SQLite on device is partially corrupted | Sync detects checksum mismatch; quarantines corrupted records; alerts user |
| **Concurrent sync of same account** | User logs in on two phones, records offline on both, then both sync | Deterministic merge: no data loss, no duplicate creation, audit trail shows both sources |

---

## 3. Edge Cases the Proposer Missed

### 3.1 Device-Level Edge Cases

- **MicroSD card removal** — User stores app data on external SD, card is removed. App must handle gracefully, not crash or lose data.
- **Phone number change** — Auth is via SMS OTP + phone number. What happens when a bank sampah operator changes their SIM card? Account recovery flow is absent from the proposal.
- **Multiple users per device** — A single HP might be used by two different bank sampah operators on different shifts. No multi-account concept.
- **Battery optimization killing background sync** — Android Doze, MIUI's aggressive battery management, and manufacturer-specific power saving will kill periodic sync. The proposal doesn't mention foreground services or WorkManager constraints.

### 3.2 Data Edge Cases

- **Zero-weight transactions** — What happens if the scale breaks and records 0 kg? Should the app reject sub-gram weights?
- **Negative weight** — Can the input field accept negative numbers? What about weights that exceed physical reason (e.g., 9999 kg of plastic bottles in one go)?
- **Duplicate QR scan** — User scans the same QR receipt twice. Does the system credit points twice?
- **QR expiry** — QR codes have no expiry mentioned. An old QR from 2025 scanned in 2026 could fraudulently claim points.
- **Category renames** — If "Botol Plastik" is renamed to "Botol PET" in the server, what happens to old offline transactions that used the old category ID?
- **Decimal precision** — Weight recorded as 1.5 kg but the QRIS payout multiplies by 1.499999 due to floating-point rounding. Over 50,000 transactions, this creates significant discrepancy.

### 3.3 Ecosystem Edge Cases

- **Pengepul goes bankrupt** — What happens to pending transactions or unredeemed points when a pengepul drops out?
- **DLH staff turnover** — New DLH staff inherits dashboard access. No mention of role-based access control (RBAC) lifecycle — onboarding/offboarding users.
- **Natural disaster** — Server goes down in a flood-prone area. Does the app continue working fully offline for weeks?
- **SIM card cloning / OTP interception** — SMS OTP is the weakest authentication mechanism. SS7 attacks or SIM swap could let an attacker hijack a bank sampah account and drain points.

---

## 4. Failure Scenarios — What Happens When Sync Conflicts?

The proposal's risk section mentions "anti-ghost data saat offline" but provides zero technical detail. Here's what needs to be tested:

### 4.1 Conflict Types & Required Behavior

| Conflict Type | Example | Desired Behavior | Test Verdict |
|---------------|---------|------------------|--------------|
| **Weight discrepancy** | Device A records 5kg, Device B records 5.5kg for same bag (same timestamp) | Flag for manual verification; neither auto-accepted | FAIL if system silently chooses one |
| **Duplicate person** | Same household registered twice by different operators offline | Match on phone number + address; merge or alert | FAIL if double data goes undetected |
| **Price change during offline** | Point value of "Botol Plastik" was 100/kg when offline data was recorded, but server now says 120/kg | Use price AT TIME of transaction, not current price — requires price snapshot in offline payload | FAIL if recalculated with current price (financial error) |
| **Delete vs. edit** | Server deleted a transaction (fraud flag), but device edited it offline and syncs the edit | Delete wins; edit is rejected with reason | FAIL if edit resurrects deleted record |
| **Out-of-order sync** | Event B happened before Event A (clock skew), but B syncs first | Vector clock or HLC detects ordering; events applied in correct causal order | FAIL if applied in sync-order |

### 4.2 Sync Conflict Test Harness Recommendations

- Build a **network condition simulator** that can throttle, drop, delay, and reorder network packets.
- Create a **clock-skew emulator** in testing to set device time ±24h from server time.
- Use **fuzzing** to inject random conflicts: duplicate IDs, overlapping timestamps, mismatched checksums.
- Every conflict resolution must be **audit-logged** — who resolved it, when, which strategy was used, what the final state is.

---

## 5. UU PDP (Data Privacy) Compliance Testing

The proposal mentions "data desentralisasi; pengguna kontrol data mereka" but this is dangerously vague.

### 5.1 UU PDP Requirements That Need Testing

| UU PDP Article | Requirement | How to Test |
|----------------|-------------|-------------|
| **Pasal 26** | Consent must be explicit for each processing purpose | Verify app shows clear consent screen for: (a) recording transactions, (b) sharing with pengepul, (c) sharing with DLH (aggregate). Test that declining one still allows basic app function. |
| **Pasal 29** | Data subject right to access their data | Test that household user can request and receive all their transaction data in machine-readable format within 3 days. |
| **Pasal 31** | Right to deletion (right to be forgotten) | Delete a user account; verify all personal data is purged from server AND queued for deletion on offline devices on next sync. |
| **Pasal 36** | Data breach notification within 14 days | Test breach detection: if sync transmits unencrypted PII, that's a breach. Verify notification mechanism. |
| **Pasal 20** | Data minimization | Verify the app does NOT collect: GPS location (unless explicit consent), contacts, SMS, photos, IMEI. Only collect what's needed for transactions. |
| **Cross-border transfer (Pasal 55)** | Data must stay in Indonesia | Verify server infrastructure is domestic. If using cloud (GCP/AWS with Indonesia region), verify contractual compliance. |
| **Pasal 15** | Purpose limitation | Test that data collected for transactions is NOT used for unrelated analytics or ad targeting without separate consent. |

### 5.2 Specific Privacy Testing Scenarios

1. **Offline data at rest** — If device is lost/stolen, is the local SQLite database encrypted? Android's `EncryptedSharedPreferences` and `Room` with `SQLCipher` should be mandatory. **The proposal doesn't mention encryption at rest.** This MUST change.
2. **Sync data in transit** — Even for periodic sync over HTTP (not HTTPS), all data must be TLS 1.3 encrypted. Test with a MITM proxy (mitmproxy) to verify no plaintext PII leaks.
3. **DLH aggregate data** — DLH sees per-kecamatan aggregate data. Test that individual household/operator data is NOT derivable from aggregates (differential privacy concerns).
4. **QR code PII** — Does the QR receipt contain personal data (name, phone)? If so, it's a privacy risk. Test QR payload contents.
5. **Analytics SDKs** — Firebase/Google Analytics are commonly used but send data to US servers — violates UU PDP Pasal 55. Verify analytics is self-hosted or uses Indonesia-resident service.

---

## 6. Financial Transaction Accuracy (QRIS) — What If Records Don't Match?

### 6.1 Critical Financial Risks

| Risk | Impact | Test Needed |
|------|--------|-------------|
| **Weight × price mismatch** | User gets overpaid or underpaid | End-to-end reconciliation: simulate 10,000 transactions offline, sync, compare server-calculated payout to device-calculated payout. Tolerance: **ZERO** — every mismatch is a bug. |
| **Duplicate payout** | User redeems same points twice (QR replay) | Test QR code one-time-use with server-side nonce verification. If QR can be scanned twice, it's a blocker. |
| **Rounding error accumulation** | Fractions of rupiah lost over millions of transactions | Use integer arithmetic (satuan rupiah, not float). Test with 1,000,000 transactions for accumulated error. |
| **Sync timing: point redemption before sync** | User redeems points while device is offline; device syncs after redemption; server already processed it | Define clear protocol: redemptions require online verification OR allow offline with lock (points frozen for 24h until sync). |
| **Bank/payment provider failure** | QRIS provider (GoPay/ShopeePay) has outage | Graceful failure: points stay in wallet, retry queue, user notified. No lost money. |

### 6.2 Reconciliation Strategy

TAUT MUST implement a **daily reconciliation job**:

1. **Device-side ledger** — Each device signs its transaction log with HMAC. Signed log is synced to server.
2. **Server-side ledger** — Server independently recalculates all payouts from raw transaction data.
3. **Reconciliation diff** — Compare device-calculated payout vs server-calculated payout. Any difference > 0 rupiah triggers an alert and halts further payouts.
4. **Audit trail** — Every financial event (weight recorded, price applied, points credited, points redeemed, QRIS transferred) is an immutable audit log entry. No updates, only appends.

### 6.3 What If Records Don't Match?

**Immediate actions:**
- **Freeze** all payouts for the affected bank sampah.
- **Flag** every mismatched transaction for manual review.
- **Notify** operator and nasabah of pending verification.
- **Investigate** root cause (hardware scale defect? typo? sync bug? fraud?).
- **Reconcile** via admin panel: admin can adjust individual records with reason code.

**Testing this:** Inject deliberate mismatches into test data and verify the system detects, flags, and freezes correctly.

---

## 7. Performance Testing on Low-End Devices

### 7.1 Target Device Profile

Based on "HP Rp500 ribuan" (~US$30):

| Component | Spec |
|-----------|------|
| RAM | 1–2 GB |
| Storage | 8–16 GB (less than 2 GB free) |
| CPU | MediaTek MT6739 / Spreadtrum SC9832E (quad-core 1.3 GHz) |
| Android | 8 Go / 9 Go / 10 Go Edition |
| Screen | 480×854 or 540×960, ~5" |
| Battery | 2000–3000 mAh |

### 7.2 Performance Test Scenarios

| Scenario | Metric | Threshold | Test Method |
|----------|--------|-----------|-------------|
| **App cold start** | Time to usable UI | < 5 seconds | Launch app 50x, measure on low-end device |
| **Transaction entry** | Time from tap "Timbang" to saved record | < 2 seconds | Automated UI test with Espresso |
| **Offline ledger** | 5,000 transactions in local DB | App size increase < 10 MB, query time < 500ms | Load test with synthetic data |
| **Sync payload size** | Bytes per daily sync | < 500 KB per day | Measure actual sync payload with Wireshark; compress with protocol buffers |
| **Memory pressure** | App with 1,000 records + background sync | < 100 MB RSS, no OOM kill | Run under Android Profiler; test on 1 GB device |
| **Battery drain** | 1 hour of typical use (30 transactions + 2 syncs) | < 5% battery drain | Test on low-end device with battery historian |
| **ANR (Application Not Responding)** | UI thread blocked | **Zero** ANRs per 100 hours of testing | Monkey test + UI automation for 24h |
| **Storage** | Total app + data after 1 year of daily use | < 200 MB | Fill with synthetic 1-year data |

### 7.3 Specific Low-End Testing Strategy

- **Primary test devices:** Realme C11 (2020), Samsung Galaxy A01, Xiaomi Redmi Go, Nokia C1 — these represent the actual target devices.
- **Android Go testing:** Enable "Android Go" configuration on emulator; test with only 1 GB RAM.
- **MIUI/ColorOS/OneUI battery optimization:** Test with manufacturer-specific battery saving ON. Verify WorkManager's `setRequiredNetworkType` still triggers sync.
- **Storage pressure test:** Fill device storage to 90% capacity and verify app handles `SQLiteDatabaseFullException` gracefully.
- **Thermal throttling test:** Run continuous transaction entry for 30 minutes; verify no UI lag from CPU throttling.

---

## 8. What MUST Change Before We Proceed

These are **blockers** — the proposal cannot proceed to technical breakdown without addressing them:

### 🔴 BLOCKER: Sync Protocol Specification

The proposal must define (minimally):
- Conflict resolution strategy (recommend: LWW with HLC + user-side flag for manual resolution)
- Sync scheduling (recommend: Android WorkManager with constraints for WiFi + charging for full sync, mobile data for lightweight sync)
- Data format for sync payload (recommend: Protocol Buffers or flatbuffers for size efficiency)
- How offline price snapshots are stored and validated
- **This is the #1 technical risk.** Without it, we cannot design tests, estimate effort, or guarantee data integrity.

### 🔴 BLOCKER: Encryption at Rest Mandate

The proposal says nothing about encrypting the local database. On an Android phone that can be lost or stolen, UU PDP Pasal 20 (data minimization) and Pasal 36 (breach notification) are violated if a lost device exposes transaction data linked to phone numbers. **All local data must be encrypted (SQLCipher or similar).**

### 🔴 BLOCKER: Financial Reconciliation Design

The proposal must document:
- How payouts are calculated (exact formula)
- How rounding is handled (integer math, not float)
- How replay attacks on QR codes are prevented (nonce + one-time-use)
- The reconciliation/daily audit job
- **Without this, TAUT cannot handle real money safely.**

### 🟡 HIGH PRIORITY: Offline Authentication Strategy

SMS OTP requires internet for the OTP step. How does a user authenticate when they first open the app offline? Proposal must clarify:
- Is there a one-time setup wizard done online?
- Can a device be "pre-authorized" (e.g., via another device with internet)?
- What happens if the phone number changes?

### 🟡 HIGH PRIORITY: Multi-Account / Device Sharing Policy

A single phone used by multiple operators must be addressed. Is there a quick-switch account feature? Or is the app single-user per device?

### 🟡 HIGH PRIORITY: QR Code Security Design

QR codes serve as legal receipts. The payload format, cryptographic signing method, and replay prevention must be specified. A QR code that can be screenshotted and reused is a critical vulnerability.

---

## 9. Recommended Testing Strategy

### 9.1 Test Pyramid (Adapted for Offline-First Mobile)

```
          ┌─────────────────────────────┐
          │    E2E / Integration         │  ← 10% of tests
          │  (Full device + server +     │
          │   payment sandbox)           │
          ├─────────────────────────────┤
          │    Sync Layer Tests          │  ← 25% of tests
          │  (Conflict resolution,       │
          │   reconciliation, offline    │
          │   → online transition)       │
          ├─────────────────────────────┤
          │    Service / Repository      │  ← 25% of tests
          │  (Business logic, pricing    │
          │   calculation, data model    │
          │   validation)                │
          ├─────────────────────────────┤
          │    Unit Tests                │  ← 40% of tests
          │  (ViewModel, converters,     │
          │   validators, edge cases)    │
          └─────────────────────────────┘
```

### 9.2 Critical Test Types

| Test Type | Tool / Method | Target |
|-----------|---------------|--------|
| **Unit tests** | JUnit 5 + MockK (Kotlin) | ViewModels, price calculation, validators, data converters |
| **Repository tests** | Room in-memory DB + Turbine (Flow testing) | DAO queries, offline CRUD, sync queue |
| **Sync conflict tests** | Custom conflict simulator | All 8 conflict scenarios from §2.2 |
| **UI tests** | Compose UI Test + Robolectric (headless) / Espresso (device) | Transaction flow, navigation, offline indicators |
| **Integration tests** | Test servers with mocked payment provider (QRIS sandbox) | End-to-end: offline record → sync → payout |
| **Performance tests** | Android Profiler, Monkey, custom benchmark | ANR, memory, battery, startup time |
| **Security tests** | mitmproxy, MobSF (Mobile Security Framework) | TLS, encryption at rest, QR payload inspection |
| **Accessibility tests** | TalkBack + custom a11y assertions | Usable by low-literacy, visually impaired users |
| **Privacy tests** | Custom privacy scanner, manual audit | UU PDP compliance checklist from §5 |
| **Monkey / Fuzz tests** | Android Monkey, libFuzzer (sync payload fuzzing) | Crash resistance, input validation |

### 9.3 Recommended Toolchain

- **Framework:** Jetpack Compose (UI) + Room (local DB) + Ktor Client (sync) + WorkManager (sync scheduling)
- **Testing:** JUnit 5, MockK, Kotlin Coroutines Test, Turbine, Compose UI Test, Robolectric (for fast headless tests)
- **CI/CD:** Buildkite or GitHub Actions with Android Emulator step for E2E tests
- **Device Farm:** Firebase Test Lab (for testing on 50+ real devices, especially low-end models)
- **Network Simulation:** ATC (Augmented Traffic Control) by Facebook or `tc` (traffic control) on Linux to simulate 2G/3G conditions
- **Security:** MobSF for static analysis, OWASP ZAP for API scanning, mitmproxy for MITM testing
- **Performance:** Android Vitals / Firebase Performance Monitoring in production; Android Studio Profiler during dev

### 9.4 Release Gates (What Must Pass Before Release)

| Gate | Criteria |
|------|----------|
| **P0 — Financial Integrity** | Zero mismatches in reconciliation test (10,000 tx) |
| **P0 — Offline Sync** | All 8 sync scenarios pass (see §2.2) |
| **P0 — No Data Loss** | Monkey test for 24h, any crash = BLOCKER |
| **P1 — Privacy** | UU PDP checklist (§5) fully passes; no PII in logs or crash reports |
| **P1 — Performance** | Cold start < 5s, no ANR, memory < 100 MB on 1 GB device |
| **P2 — Security** | TLS verified, local DB encrypted, QR payload non-replayable |
| **P2 — Accessibility** | App usable with TalkBack, minimum touch target 48dp |

---

## 10. Summary — QA Verdict

**Overall assessment:** The TAUT proposal addresses a real, urgent problem with a well-considered approach. However, it is **not ready for technical breakdown** in its current form.

**Three primary blockers (must resolve before we proceed):**

1. **Sync protocol specification** — Without defining conflict resolution, the entire data integrity model is an unknown.
2. **Encryption at rest** — A lost device becomes a UU PDP violation; this is non-negotiable.
3. **Financial reconciliation design** — Handling real money requires provable correctness, not just "it works on my device."

**Secondary gaps that need attention in Phase 0 (before MVP coding starts):**
- QR security design (nonce, signing, expiry)
- Offline authentication flow
- Multi-account / device-sharing policy
- Price snapshot strategy for offline transactions
- Device-clock handling in distributed timestamping

**QA is ready to proceed** once these blockers are resolved. The recommended testing strategy above provides a comprehensive framework that catches issues early (unit tests), at the critical sync layer (dedicated conflict tests), and end-to-end (reconciliation with financial sandbox).

---

*Prepared by: QA Engineer*  
*23 Juni 2026*
