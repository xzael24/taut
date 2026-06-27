# Round 4: Final Decision — Frontend Engineer

## [AGREE] ✅ Proceed to Technical Execution

---

## What Changed Since My Original Review

The Proposer addressed **all 6 of my 🔴 BLOCKERS** and **all 6 🟡 recommendations** from Round 2:

| # | My Blocker | v2 Resolution | Status |
|---|-----------|--------------|--------|
| 1 | No tech stack decision | **Locked:** Kotlin Native + Jetpack Compose + Room + WorkManager + Hilt DI. Flutter/RN explicitly rejected. Min API 26. | ✅ |
| 2 | Camera scope undefined | **Camera scanning deferred to Fase 2.** MVP = QR *generation* (display on operator screen) + manual transaction ID entry. Saves 4-6 weeks of highest-risk FE work. | ✅ |
| 3 | Target device matrix vague | **5 specific phone models named:** Xiaomi Redmi A2, Samsung Galaxy A05, Advan G5, Evercoss A66, Nokia C1. All to be purchased for dev/QA before Sprint 1. | ✅ |
| 4 | Memory budget absent | **300MB hard ceiling. Per-component allocation defined:** Compose UI ~60MB, Room DB ~50MB, Coil cache ~40MB, WorkManager sync ~20MB, etc. Memory monitoring in CI. | ✅ |
| 5 | Compose vs View deferred | **Compromise accepted:** Compose throughout for MVP (no camera screens). Re-evaluate hybrid (View for camera) in Fase 2 when camera scanning enters scope. Pragmatic. | ✅ |
| 6 | Audio feedback missing | **Added to MVP spec:** Android TTS confirmation after every transaction. Works offline. Indonesian voice. ~50 lines Kotlin — highest-ROI FE feature. | ✅ |

| # | My Recommendation | v2 Resolution | Status |
|---|-----------------|--------------|--------|
| 7 | Shared-device / kiosk mode | **Added to MVP:** PIN-secured operator profiles, quick-switch, up to 5 operators per device. | ✅ |
| 8 | Offline-first UI contract | **Added:** Every screen 100% functional offline. Per-transaction sync status (✅ synced / 🔄 pending / ❌ failed). No "No internet" errors. | ✅ |
| 9 | Photo evidence storage strategy | Memory budget covers Coil cache. Photos optional in MVP. Sync-first architecture implies upload-and-evict. Adequate for MVP. | 🟡 OK |
| 10 | Baseline Profiles | Not explicitly addressed. Minor Sprint-1 optimization — not a blocker. | 🟡 OK |
| 11 | Graceful degradation (no-camera) | Less relevant since camera not in MVP. Manual entry fallback documented for Fase 2. | 🟡 OK |
| 12 | Progressive disclosure (Beginner/Expert) | **Added:** Two interaction modes designed from day one. Default: Beginner mode. | ✅ |

---

## Remaining Minor Concerns (Non-Blocking)

1. **Baseline Profiles (Jetpack Macrobenchmark):** Not mentioned in v2. I'd still recommend adding this to Sprint 1 — it reduces cold-start from ~8s to ~2s on eMMC storage. Trivial to add, high impact. I'll raise this during Sprint planning.

2. **Photo-eviction strategy:** The long-term strategy for waste-evidence photos (upload on sync, delete from local after server confirmation) is implied by the sync architecture but not explicitly documented. Again, a Sprint-1 detail, not a blocker.

3. **Compose-vs-View re-evaluation trigger in Fase 2:** The proposal says "re-evaluate when camera scanning enters scope" but doesn't define what criteria drive the decision (e.g., "if memory pressure >280MB during camera preview, switch to View"). I'll propose adding this criteria to the Fase 2 spec.

None of these warrant blocking the project. They are normal refinement items for technical execution.

---

## Why I'm Confident to Proceed

1. **The tech stack is correct and locked.** Kotlin Native + Compose + Room + WorkManager is the only stack that meets the <15MB APK + 1GB RAM targets. This was my #1 concern. It's resolved.

2. **The hardest FE feature (camera scanning) is deprioritized.** Moving camera to Fase 2 removes the single riskiest FE deliverable from MVP, giving us 3 months to validate the core value proposition before tackling device-fragmentation hell.

3. **The device strategy is concrete.** Five specific phone models, purchased before Sprint 1, tested with a 3-screen PoC. This is infinitely better than "HP Rp500 ribuan."

4. **The memory budget is defined and enforceable.** 300MB ceiling, per-component allocations, CI monitoring. Without this we would ship an OOM-prone app. With it, we have a fighting chance.

5. **The UX design philosophy aligns with FE constraints.** "Digital notebook" layout, photo-based categories, audio feedback, no hamburger menus, no swipe gestures, max 3 steps per action. Every principle maps to a measurable engineering constraint.

6. **The offline-first contract is explicit.** Every screen works offline. Sync status is per-transaction. This is the minimum viable trust-building mechanism for operators.

---

## Final Verdict

**✅ AGREE — Proceed to technical execution.**

The v2 proposal has transformed from a vision document into an execution plan that respects the hard constraints of the target devices and users. The Proposer's response to my FE review was thorough, humble, and actionable — all 6 blockers resolved, all 6 recommendations addressed.

From an FE perspective, the remaining risks (camera fragmentation in Fase 2, memory pressure under multi-tasking, baseline profiles) are **normal engineering risks** managed during sprints, not **project-level existential risks**.

> *"The hardest truth: Building an app that works at all on a 1GB RAM phone with fixed-focus camera and eMMC storage is hard. Building one that operators trust and love enough to abandon their paper ledger is much harder."*
>
> — My original review, Round 2
>
> The v2 proposal proves the team understands this truth and has built the plan around it. Let's go build.

---

*Frontend Engineer*
*23 Juni 2026*
