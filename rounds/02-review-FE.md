=== FE REVIEW ===

# TAUT Proposal — Frontend Engineering Review

**Reviewer:** Frontend Engineer
**Date:** 23 Juni 2026
**Status:** ⚠️ NOT YET APPROVED — significant FE-specific gaps must be addressed before any code is written

---

## 1. Android App Development Approach: Kotlin vs Flutter vs React Native

The proposal does NOT specify a tech stack. This is the single most consequential missing decision for FE. Here's the unvarnished comparison.

### Comparison Table

| Dimension | Kotlin (Native) | Flutter | React Native |
|-----------|----------------|---------|--------------|
| **Baseline APK size** | ~3–4MB (empty Compose app) | ~7–8MB (Dart VM + engine) | ~8–12MB (Hermes + RN core) |
| **Headroom for 15MB budget** | ✅ 11–12MB for app logic | ❌ 7–8MB left — tight | ❌ 3–7MB left — extremely tight |
| **RAM baseline (app only)** | ~40–60MB | ~60–90MB | ~70–110MB |
| **Camera/QR integration** | Native CameraX API — full control | Platform channels — adds latency | react-native-camera — bridge overhead |
| **Offline-first (Room DB)** | First-class — Room is Jetpack standard | Hive/Isar — not Android-optimized | AsyncStorage — no relational queries; WatermelonDB is immature |
| **Sync (WorkManager)** | First-class — built for Doze/App Standby | WorkManager unavailable — must use cron-like patterns | WorkManager unavailable — JS background exec is unreliable |
| **Performance on 1GB RAM** | ✅ Best — direct to GPU with no runtime | 🟡 Dart GC can cause jank under memory pressure | 🔴 JSBridge + GC overhead causes frame drops |
| **Talent pool (Indonesia)** | Medium | Medium-High | High |
| **iOS path** | Must rewrite | Shared codebase | Shared codebase |

### Verdict

**Kotlin + Jetpack Compose + Room + WorkManager is the only realistic choice for the <15MB APK + 1GB RAM target. Flutter and React Native cannot meet the constraints.**

- Flutter's 7MB baseline swallows half the APK budget before a single screen is built. The Dart VM also adds ~30MB+ RAM overhead that pushes us dangerously close to OOM on 1GB devices.
- React Native's Hermes engine baseline is even worse. More importantly, RN's background task support (critical for offline sync) is fundamentally unreliable on Android — `react-native-background-*` packages are notoriously flaky across device manufacturers.
- **Kotlin Native gives us:** direct access to CameraX, WorkManager, Room/SQLCipher, and Android Keystore — all critical for offline-first, camera-integrated, secure operation on cheap phones.

**Counter-argument acknowledged:** Kotlin means iOS requires a rewrite. But the MVP is Android-only (per the proposal). By the time iOS matters (Fase 3+), the platform will be validated. Premature cross-platform adds risk with zero MVP benefit.

---

## 2. UI Constraints on Cheap Android Phones — Hard Numbers

The proposal says "HP Rp500 ribuan" and "Android 8+" as passing mentions. These constraints are actually **the defining technical boundary for every FE decision**.

### Target Device Profile (Based on Market Reality, 2026)

| Spec | Typical Value | FE Implication |
|------|--------------|----------------|
| **RAM** | 1–2 GB (Android Go or near-Go) | App memory budget: ~300MB max at peak. OS uses ~500–700MB. |
| **Storage** | 8–16 GB eMMC | App + data must stay under 15MB APK + ~50MB local DB max. No storing bitmap QR images. |
| **Screen** | 5.0–5.5", 480×854 or 720×1280 | Portrait-only layout. No horizontal scrolling. Font size 18sp minimum. |
| **CPU** | MediaTek Helio A22 / Spreadtrum SC9832 (quad-core 1.4–1.8 GHz) | No heavy animations. No Live Wallpapers. No WebView. |
| **Camera** | 5–8 MP, fixed focus (no AF on cheapest models) | QR scanning will be SLOW. Need visual alignment guide + tap-to-capture fallback. |
| **Android version** | 8.0–10 (Android Go) | Target API 26 minimum. Android Go has stricter background limits — WorkManager is mandatory, not optional. |
| **Battery** | 3000–4000 mAh, charges once daily | No wakelocks. No polling-based sync. WorkManager with battery-constrained scheduling. |
| **Network** | 3G/4G (often no VoLTE), intermittent | Sync must be asynchronous, never blocking UI. Delta sync with small payloads. |

### The 300MB Memory Ceiling

```text
1GB Device Memory Map:
┌────────────────────────────────────────────────────┐
│  Android OS + System Services           ~500-600MB │
├────────────────────────────────────────────────────┤
│  Available for TAUT App                  ~350-500MB │
│  ┌──────────────────────────────────────────────┐  │
│  │  Jetpack Compose UI tree + rendering  ~60MB  │  │
│  │  CameraX preview buffer               ~80MB  │  │
│  │  Room DB + SQLite pages + cache       ~50MB  │  │
│  │  Coil image cache (waste photos)      ~40MB  │  │
│  │  WorkManager sync engine              ~20MB  │  │
│  │  ZXing QR processing + buffers        ~30MB  │  │
│  │  App code + DEX + native libs         ~30MB  │  │
│  │  Misc (animations, serialization)     ~20MB  │  │
│  ├──────────────────────────────────────────────┤  │
│  │  TOTAL (peak)                        ~330MB  │  │
│  │  HEADROOM                             ~20-170MB │
│  └──────────────────────────────────────────────┘  │
│                                                    │
│  ⚠️ If camera is active while sync runs          │
│     during a Compose layout recalculation:        │
│     OOM is a real risk on 1GB devices              │
└────────────────────────────────────────────────────┘
```

**Decision required:** This memory ceiling means we cannot run camera + sync + heavy UI updates simultaneously. We need a clear protocol: release camera immediately after successful scan, defer sync to when app is idle, and potentially use View system (not Compose) for camera-heavy flows.

### Jetpack Compose vs View System — A Hard Tradeoff

| Concern | Compose | View System (XML) |
|---------|---------|-------------------|
| Modern & maintainable | ✅ Yes | ❌ Legacy |
| Memory overhead | ~30–60MB (Compose runtime + recomposition) | ~10–20MB |
| Performance on 1GB | 🟡 Slower first-frame, recomposition cost | ✅ Stable, predictable |
| Camera integration | 🟡 AndroidView interop adds complexity | ✅ Direct in XML layouts |
| Learning curve | Higher | Lower for senior Android devs |
| Google investment | ✅ Primary | 🟡 Maintenance mode |

**My recommendation:** Hybrid approach. Use **Compose for data-display screens** (history, dashboard, prices) where the UI is simple and declarative rendering shines. Use **View system (XML) for camera/scanning screens** where memory predictability and camera lifecycle control are critical. This adds some maintenance overhead but is the pragmatic choice for 1GB devices.

---

## 3. Icon-Heavy vs Text-Heavy UI for Low-Literacy Users

The proposal mentions "UI icon-based minimalis" in one risk-mitigation row. This is **wildly insufficient** — the entire design philosophy must be built around extreme low-literacy constraints.

### The Real Profile

- **Literacy:** Can read basic Indonesian, but *uncomfortable* reading paragraphs. Numbers are universal.
- **Digital familiarity:** Many operators have only ever used WA (WhatsApp) for voice notes and phone calls. They do not understand "swipe," "dashboard," "sync," "menu drawer."
- **Mental model:** Paper ledger. Physical money. Face-to-face transactions. Every digital action must map to an existing physical action.

### Design Principles for TAUT

```text
┌─────────────────────────────────────────────────────────────┐
│                 PRINCIPLE: ONE SCREEN, ONE ACTION            │
│                                                              │
│  ❌ DON'T:                                                   │
│     [Header bar]  [Notifications]  [Search]  [Menu ≡]      │
│     ┌────────────────────────────────────────────────────┐  │
│     │  📊 Riwayat hari ini      📤 Sync status: ○ ○ ○  │  │
│     │  ─────────────────                              │  │
│     │  ... crowded table with multiple columns ...      │  │
│     └────────────────────────────────────────────────────┘  │
│                                                              │
│  ✅ DO:                                                      │
│     ┌────────────────────────────────────────────────────┐  │
│     │                                                    │  │
│     │            Timbang & Catat Sampah                   │  │
│     │                                                    │  │
│     │    ┌──────────┐    ┌──────────┐                    │  │
│     │    │   ⚖️      │    │   📦      │                    │  │
│     │    │ Timbang  │    │ Pilih    │                    │  │
│     │    │ Baru     │    │ Kategori │                    │  │
│     │    └──────────┘    └──────────┘                    │  │
│     │                                                    │  │
│     │    ┌──────────┐    ┌──────────┐                    │  │
│     │    │   📋      │    │   💰      │                    │  │
│     │    │ Riwayat  │    │ Harga    │                    │  │
│     │    └──────────┘    └──────────┘                    │  │
│     │                                                    │  │
│     └────────────────────────────────────────────────────┘  │
│                                                              │
│  Each tile: 80x80dp icon + 24sp text label below             │
│  4 tiles max per screen. 2 rows of 2.                        │
│  NO hamburger menu. NO toolbar icons.                         │
└─────────────────────────────────────────────────────────────┘
```

### Icon System Requirements

1. **Every icon MUST be paired with text label** — never icon-only. Low-literacy users use text-on-icon as a fallback.
2. **Icons must be PHOTO-BASED for waste categories** — not vector illustrations. An operator recognizes "Aqua botol 600ml" not a generic "PET bottle" icon.
3. **Color-code by action type:** Green (timbang/tambah) → primary action. Blue (harga/info) → information. Yellow (riwayat) → history. Red → error/delete.
4. **Minimum touch target:** 56x56dp (exceeds WCAG 48x48dp for this user group).
5. **Contrast:** 4.5:1 minimum for all text. High contrast mode as the DEFAULT theme, not an option.
6. **Audio layer:** Text-to-speech confirmation after every transaction ("Tersimpan: 5 kg kardus, Rp 7.500"). Android's TTS API works offline and in Indonesian. This is cheap to implement and dramatically improves trust.

### Waste Category Selection — The Hard UX Problem

Cheap phone cameras have fixed focus. Waste items have similar shapes/colors (clear PET vs white PET vs HDPE). **Icons alone are insufficient for distinguishing waste categories.**

**FE concern:** Loading 20+ category photos must not bloat the APK. Solution: bundle aggressively optimized WebP files (~5-10KB each, total ~200KB for all categories). Cache in memory via Coil with small cache size (20MB max).

---

## 4. QR Code Generation on Device

### Technical Feasibility: ✅ Achievable

| Aspect | Detail |
|--------|--------|
| **Library** | ZXing core (Android fork, ~50KB) or custom Kotlin QR writer |
| **Performance** | QR Version 3 (29×29, ~220 chars) renders in <50ms on Helio A22 |
| **Offline** | ✅ Generation does not require internet |
| **Memory** | ~5–10MB for the QR bitmap buffer. Release immediately after generation. |
| **APK impact** | Negligible (<100KB for QR generation code) |

### The FE Problem: What to Put in the QR

The architect's proposal (HMAC-signed JSON payload) is sound, but has FE implications:

```kotlin
// Proposed QR payload JSON
{
  "v": 1,
  "tx": "taut-01j7x...",   // UUIDv7
  "bs": "BSA-42069",        // Bank Sampah ID
  "wc": "PET-BOTOL",        // Waste category code
  "wt": 2.5,                // Weight in kg
  "ts": 1719134400,         // Epoch seconds
  "s": "a3f8c2..."          // HMAC-SHA256 signature
}
```

**FE implementation concerns:**

1. **Device key storage:** The HMAC key must be stored in Android Keystore (hardware-backed if available). On 1GB devices without hardware-backed keystore (common in cheap phones), we fall back to Android Keystore software implementation. This is secure enough for MVP.

2. **Clock dependency:** `ts` is device epoch. On cheap phones without network time, the clock is frequently wrong. Server can accept clock skew of ±24h in MVP, but this is a gap for later.

3. **Key rotation:** When operator changes phones, the old device key is lost. New phone generates new key. Old QR codes (signed with old key) must still be verifiable. Solution: server stores key history per bank sampah.

4. **No bitmap storage:** QR codes should NOT be stored as bitmap images in Room DB. Store the ~220-char JSON payload string and generate the QR bitmap on-demand for display. This saves massive storage.

---

## 5. Camera Integration for QR Scanning

### Technical Feasibility: 🟡 Achievable but HIGH RISK on Cheap Phones

### Why Camera is the Trickiest FE Feature

```text
QR SCAN FLOW ON HP Rp500ribu:

  1. Open camera              → CameraX init: ~1-2 seconds
  2. Auto-focus               → Fixed focus camera: NO auto-focus
                                → User must move phone closer/farther
  3. Detect QR                → ZXing in continuous preview mode
                                → CPU decode frames: ~200-500ms each
  4. Low light performance    → No flash LED on cheapest phones
                                → QR often undetectable
  5. Success                   → Vibrate + beep
  6. Release camera            → CRITICAL: release immediately to free ~80MB RAM
```

### Camera Integration Strategy

**Option A: Continuous Scan (Default)**
- CameraX + ZXing analyzer in `ImageAnalysis` use case
- Frame analyzed at 5 FPS (not 30 — saves CPU and battery)
- Green overlay box as alignment guide
- Tap screen to toggle flash (if available)

**Option B: Tap-to-Capture Fallback (Mandatory)**
- User taps capture button → single frame captured → scanned
- Works when auto-detection is too slow (fixed focus cameras)
- Slower UX but more reliable

**Option C: Manual Input Fallback (Mandatory)**
- Operator types the transaction ID written on the physical slip
- **This must exist for every QR-scanning screen** — cannot assume camera works

### The Biggest Camera Risk: Device Fragmentation

| Problem | Impact | Mitigation |
|---------|--------|------------|
| Fixed focus on 60%+ of target devices | QR blurry when too close/too far | Show distance guide (15-20cm); tap-to-capture fallback |
| No autofocus + no flash | Unreadable in low light | Screen brightness boost; manual input fallback |
| Camera HAL fragmentation (MediaTek vs Qualcomm) | CameraX init fails silently | Graceful fallback to manual input — no crash |
| Memory pressure during scan | OOM if sync runs simultaneously | Release camera before triggering sync |
| Orientation handling | Landscape camera on portrait device | Force camera preview to match device orientation |

### Critical Decision: Is Camera Even Needed in MVP?

**The UX designer's recommendation is correct:** If SMS receipt is the primary proof-of-deposit mechanism, the QR scanning flow becomes secondary. The nasabah does not need to display a QR — they receive an SMS.

**FE position:** Deprioritize camera-based QR scanning from MVP. Build QR *generation* (display on operator's screen for nasabah to photograph) and manual entry (operator types transaction ID). Camera scanning can come in Fase 2 once the core flow is validated.

This saves us from the hardest FE problem (reliable QR scanning on fixed-focus, no-flash cameras) while the product finds product-market fit.

---

## 6. Trickiest FE Challenge

### Winner: Memory Management on 1GB RAM Devices Under Multi-Tasking

Not camera integration alone. Not offline sync alone. **The intersection of camera + Room DB + Compose rendering + WorkManager sync all competing for ~300MB of usable memory on a 1GB device.**

### The Nightmare Scenario

```text
06:00 AM — Operator opens TAUT
  → Compose renders main screen → ~60MB used

06:05 AM — Nasabah arrives with 20kg mixed waste
  → Operator taps "Timbang"
  → Camera opens for optional photo evidence → +80MB (total: 140MB)
  → Room DB loads waste categories → +20MB (total: 160MB)
  → Coil loads category thumbnails → +30MB (total: 190MB)
  → Operator selects "Kardus", inputs 5kg, saves
  → QR generation → +10MB (total: 200MB)
  → Camera still active for next transaction
  → WorkManager triggers sync in background → +20MB (total: 220MB)
  → Android kills background services to reclaim memory
  → Sync fails silently → data not replicated

⚠️ Total: 220MB (within budget but NO HEADROOM)
⚠️ If any one component leaks (e.g., CameraX buffer not released):
     → 280MB → 320MB → OOM → App crash → "Ghost data" fears validated
```

### Runner-Up: Offline Data Integrity When Device Clock is Wrong

Cheap Android phones without a SIM card or with airplane mode often have system clocks off by hours, days, or even months. Since TAUT's sync protocol relies on timestamps for ordering:

- A transaction timestamped "yesterday" (but actually 3 hours ago per real time) creates ordering conflicts.
- The "last sync cursor" (timestamp-based) breaks if the device clock jumps.
- Solution: Use server-assigned Lamport clocks for ordering. Always send local timestamp + device boot time as metadata so server can estimate drift.

### Honorable Mention: App Startup Time

On eMMC storage (not UFS, which is what mid-range phones use), app cold start can take 5-10 seconds. This is unacceptable for an operator who needs to record a transaction NOW. Must implement:
- Splash screen with immediate responsiveness (no blank screen)
- Database pre-warming: load categories and last price list during splash
- Baseline Profile (Jetpack Macrobenchmark) to pre-compile critical code paths
- Baseline Profile eliminates ~30% of first-frame rendering time

---

## 7. What MUST Change Before We Proceed

### 🔴 BLOCKERS — Non-Negotiable Before Sprint 1

| # | Issue | Current State | Required Action | FE Impact |
|---|-------|--------------|-----------------|-----------|
| 1 | **No tech stack decision** | Not specified | **Lock: Kotlin + Jetpack Compose (hybrid with View for camera) + Room + WorkManager.** Reject Flutter/RN for this target device class. | Without this, we cannot estimate APK size, memory budget, or build tooling. |
| 2 | **Camera scope undefined** | "QR code" mentioned but no detail on who scans what | **Decide now: is camera scanning MVP or Fase 2?** My recommendation: MVP = QR generation (display) + manual entry. Camera scanning = Fase 2. | Saves 4-6 weeks of camera integration work and avoids the highest-risk FE feature. |
| 3 | **Target device matrix not defined** | "HP Rp500 ribuan" — too vague | **Name 3-5 specific phone models** (e.g., Xiaomi Redmi A2, Samsung Galaxy A05, Advan G5, Evercoss A66) that we test on. Buy them for the dev team. | FE cannot optimize for "average cheap phone" — need real devices. |
| 4 | **Memory budget not established** | No budget | **Define per-component memory allocation** (Compose: 60MB, Camera: 80MB, Room: 50MB, etc.) with a hard ceiling of 300MB. Add memory monitoring to CI. | Without this, we ship an app that crashes on target devices. |
| 5 | **Compose vs View decision deferred** | Proposal assumes "modern UI" without constraint awareness | **Build a 3-screen proof-of-concept** on an actual Rp500k phone: one screen in Compose, one in View, measure memory and rendering performance. Decide based on data. | Wrong choice here means rewriting UI 6 months in. |
| 6 | **Audio feedback not in spec** | Not mentioned | **Add voice confirmation to requirements.** Android TTS is free, offline, and trivial to implement (~50 lines of Kotlin). It is the single highest-ROI FE feature for low-literacy operators. | Low effort, high trust impact. Should be in MVP. |

### 🟡 STRONG RECOMMENDATIONS — Should Fix Before Fase 2

| # | Issue | Why |
|---|-------|-----|
| 7 | **Shared-device / kiosk mode** | Most bank sampah have ONE phone shared by multiple operators. Without profile switching or kiosk mode, the app forces a single-user assumption that doesn't match reality. |
| 8 | **Offline-first UI contract** | Every screen must be designed to work fully offline — cached prices, no empty states, no "No internet" errors. The UI must communicate sync status per-transaction (✅ synced, 🔄 pending). |
| 9 | **Photo evidence storage strategy** | If fraud prevention requires waste photos, plan storage budget: 50KB per JPEG at 480p. 100 transactions/day × 30 days × 50KB = 150MB/month. This exceeds local storage budget. Solution: upload photos on sync, remove from local after confirmation. |
| 10 | **Baseline Profiles** | Use Jetpack Macrobenchmark to generate Baseline Profiles for Android 8+ devices. This pre-compiles critical code paths and reduces cold-start time from ~8s to ~2s. |
| 11 | **Graceful degradation for no-camera devices** | Some Rp500k phones have terrible cameras or broken camera HAL. App must work completely without camera — manual entry for everything. |
| 12 | **Progressive disclosure: Beginner vs Expert mode** | After 2-4 weeks, operators want speed: quick-add buttons, memorized categories, minimal taps. Design two interaction modes from day one. |

---

## 8. FE Architecture Decision Record Needed

These decisions MUST be made and documented before Sprint 1:

| Decision | Options | Recommended | Rationale |
|----------|---------|-------------|-----------|
| **UI Framework** | Compose / View / Hybrid | **Hybrid** — Compose for data screens, View for camera | Memory constraints on 1GB devices |
| **Image loading** | Coil / Glide / Picasso | **Coil** | Kotlin-native, coroutine-based, smallest APK impact (~200KB) |
| **QR library** | ZXing / QRGen / Custom | **ZXing (Android fork)** | Most battle-tested; lightweight fork available |
| **Camera API** | CameraX / Camera2 | **CameraX** | Better device fragmentation handling |
| **Local DB** | Room / SQLDelight / Realm | **Room** | Jetpack standard; best WorkManager integration |
| **DI** | Hilt / Koin / Manual | **Hilt** | Standard for Android; compile-time safety |
| **Minimum API** | API 24 / API 26 / API 28 | **API 26 (Android 8.0)** | Covers 95%+ of Indonesian devices per Google Play stats |
| **App architecture** | MVVM / MVI / Redux | **MVVM** | Google-recommended; simpler than MVI for this user group |
| **Sync trigger** | Periodic / Push / Poll | **WorkManager periodic (15 min) + push notification wake-up** | Battery-friendly; survives Doze |
| **TTS engine** | Android TTS / Google Cloud TTS | **Android TTS (offline)** | Free, offline, Indonesian voice available |

---

## 9. FE Risk Register

| Risk | Likelihood | Impact | Mitigation |
|------|-----------|--------|------------|
| **OOM on 1GB devices** | HIGH | CRITICAL | Memory budget; release camera immediately; no Compose for camera screens; monitor in CI |
| **Camera scan fails on fixed-focus phones** | HIGH | HIGH | Manual entry fallback; distance guide UI; tap-to-capture |
| **APK exceeds 15MB** | MEDIUM | HIGH | Kotlin native; no WebView; aggressive ProGuard/R8; monitor per-feature size in CI |
| **Compose recomposition jank on low-end CPU** | MEDIUM | MEDIUM | Hybrid approach; limit composable depth; use `remember` aggressively |
| **Device clock skew breaks sync ordering** | HIGH | MEDIUM | Lamport clocks on server; local timestamp is advisory, not authoritative |
| **eMMC slow I/O on DB writes** | MEDIUM | MEDIUM | Use WAL mode; batch writes; avoid writing per-transaction if batched |
| **App uninstallation due to storage pressure** | MEDIUM | MEDIUM | Auto-cleanup of old synced records; clear QR bitmap cache; keep DB < 50MB |
| **Operator resistance to digital input** | HIGH | HIGH | Audio feedback; "digital notebook" layout; companion physical book |

---

## 10. Summary Scorecard (FE Perspective)

| Dimension | Score (1-5) | Key Gap |
|-----------|-------------|---------|
| Problem understanding | 🟢 5/5 | Excellent field research; constraints are real |
| Tech stack awareness | 🔴 1/5 | No stack specified; Flutter/RN would silently fail on target devices |
| Device constraint understanding | 🟠 2/5 | Mentions "Rp500 ribuan" but no device matrix, no memory budget, no specific phone models |
| QR generation | 🟡 3/5 | Concept is sound; FE can deliver this. HMAC key management needs design. |
| Camera integration | 🟠 2/5 | Underspecified; doesn't account for fixed-focus, no-flash reality of target phones |
| Offline-first UX | 🟠 2/5 | No per-screen offline states designed; sync status not surfaced to user |
| Accessibility for low-literacy | 🟡 3/5 | "Icon-based" is not a strategy; needs photo-based categories, audio feedback, digital notebook mental model |
| Memory management | 🔴 1/5 | Not addressed at all — this will be the #1 cause of crashes |
| Shared-device support | 🔴 1/5 | Not addressed — app assumes 1:1 user:device, which is wrong for bank sampah |
| Overall FE Readiness | 🟠 **2.1/5** | Strong vision. Serious FE gaps. Must resolve 6 blockers before Sprint 1. |

---

## Final Message to the Team

> The TAUT vision is compelling and the problem is real. From an FE perspective, the proposal correctly identifies the key constraints (offline-first, cheap phones, low-literacy users) but **drastically underestimates the engineering difficulty of delivering a polished experience within those constraints.**
>
> **The hardest truth:** Building an app that works *at all* on a 1GB RAM phone with fixed-focus camera and eMMC storage is hard. Building one that operators *trust and love* enough to abandon their paper ledger is much harder. Every MB and every millisecond counts.
>
> **The good news:** Kotlin Native + Room + WorkManager is the right stack. The 15MB APK target is achievable. QR generation is straightforward. Audio feedback is cheap. The "digital notebook" layout maps to existing mental models.
>
> **My conditions for proceed:**
> 1. Lock Kotlin Native (no Flutter/RN)
> 2. Deprioritize camera scanning from MVP
> 3. Define the device matrix and buy target phones
> 4. Create and enforce the memory budget
> 5. Build a Compose-vs-View proof-of-concept on actual hardware
> 6. Add voice confirmation to MVP spec
>
> Without these, we will ship an app that crashes silently on the phones that need it most, and operators will go back to their notebooks — this time with a bitter taste of "technology" that will take years to undo.

---

*Review disusun oleh Frontend Engineer*
*23 Juni 2026*
