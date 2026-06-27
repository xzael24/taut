=== PM REVIEW ===

## TAUT Proposal — Product Management Critique

### Role: Product Manager

---

### 1. Overall Viability: Would People Actually Use This?

**Verdict: Yes — but with important caveats.**

The proposal correctly identifies a real, painful problem. Bank sampah operators using paper ledgers in 2026 is both a tragedy and an opportunity. The pain is genuine: manual rekap takes hours, laporan ke dinas is always late, and nasabah have no trust in the system because there's no transparency.

**Why it could work:**
- Offline-first is non-negotiable and the proposal acknowledges this correctly. An Android app that works on a Rp500k phone without data is the right technical bet.
- The flow (rumah tangga → bank sampah → pengepul → pabrik) mirrors the actual existing chain — no behavioral overhaul required.
- QRIS integration is smart; 50M+ users means the payout infrastructure is already in place.

**Three viability risks the Proposer underweights:**

1. **Operator age/digital literacy gap is not just a UX problem — it's a trust problem.** Many bank sampah operators are ibu-ibu PKK in their 40s–60s. Handing them an Android app is Step 1; getting them to trust it enough to abandon their paper buku is Step 2, which is much harder. The proposal's mitigation ("UI icon-based minimalis; onboarding via komunitas/kader") is vague. The real answer: dedicated pendamping (paid or incentivized) for the first 2–3 months per location.

2. **Who is the REAL primary user?** The proposal says P1 = Operator Bank Sampah, but the solution is strongest for P3 (Nasabah/Rumah Tangga) and P2 (Pemulung) — the QR code receipt and poin system. Operators get a dashboard they never asked for. The person who does data entry (operator) doesn't directly benefit from the data — the pemda does. This is a classic multi-sided market problem: the side that does the work is not the side that gets the value.

3. **Critical mass problem.** A single bank sampah on TAUT is useless — the value of the network grows with the number of pengepul and pabrik on the other side. If only a few pengepul are on the platform, operators see no reason to switch from their existing pengepul relationships. The proposal skips over chicken-and-egg cold-start risk.

---

### 2. Prioritization — Are the Phases Right?

**No. The phases need significant reordering.**

**Current proposal:**
- Fase 1 (MVP): Catat app + harga acuan + dashboard operator
- Fase 2: Poin QRIS/e-money + harga real-time + dashboard DLH
- Fase 3: Pickup + TPA integrasi + pabrik onboarding

**The problem:** The proposed MVP gives operators a data-entry tool with no immediate, tangible benefit to THEM. The poin/reward system (the thing that motivates nasabah and gives operators something to offer) is pushed to Fase 2.

**My revised ordering:**

- **REAL MVP (Fase 0 — 2 months, not 3):** Catat app offline + QR receipt untuk nasabah + dashboard operator basic. STOP. No harga acuan yet. Ship this to 20 bank sampah, learn, iterate.

- **Fase 1 (Month 2–4):** Poin digital untuk nasabah (tukar ke pulsa atau sembako — simpler than QRIS integration which requires partnership deals). This is the VIRAL LOOP: nasabah tell neighbors "I got free pulsa for my botol plastik." Harga acuan can still be static/manual at this stage.

- **Fase 2 (Month 4–7):** QRIS/e-money payout + harga real-time from pengepul + dashboard DLH.

- **Fase 3 (Month 7–12):** Pickup request + pengepul/pabrik onboarding tools + ESG reporting.

**Why this matters:** The MVP as written gives operators more work (input data) without giving them anything they value. The poin system is the hook that gets nasabah to bring more sampah, which gives operators reason to use the app, which generates the data the pemda wants. The incentive chain must start with the NASABAH, not the operator or the pemda.

---

### 3. The REAL MVP (Bare Minimum)

**The current MVP scope is too big for a true MVP.**

Scrap the harga acuan feature from MVP. Here's what the REAL MVP must be:

**Non-negotiable:**
- Android app (offline-first, <15MB)
- Timbang + pilih kategori + catat berat
- QR receipt untuk nasabah (proof of deposit)
- SMS-based or QR-based session sync (since many operators share one HP)
- Web dashboard showing: total volume per hari/minggu/bulan, per kategori
- Single-user auth via phone number (OTP)

**Nice-to-have, NOT MVP:**
- Harga acuan (manual updates are fine for first 3 months)
- Pengepul pricing module
- Poin system (controversial — I want this in Fase 1, not MVP, because it requires partnerships)
- Dashboard DLH (the pemda is not your customer yet)
- QRIS payout integration

**The REAL MVP must be tested on ONE question: "Does this save the operator time compared to their buku catatan?" If the answer after 2 months with 20 bank sampah is no, pivot or kill.**

---

### 4. User Acquisition — Getting Bank Sampah Operators to Adopt

This is the hardest problem in the proposal and the most underdeveloped. The proposal spends 2 sentences on it.

**The hard truth:** Bank sampah operators are not sitting around waiting for an app. They have survived years without digital tools. The person who convinced them to try TAUT cannot be a stranger.

**Practical acquisition strategy (what MUST happen before Fase 1 launch):**

1. **Find the 1% early adopters.** Target the ~500 bank sampah that already use WhatsApp groups to share prices and coordinate. These are the digitally literate operators. Find them via bank sampah associations (ASOBSI — Asosiasi Bank Sampah Indonesia), DLH contacts, or direct field visits.

2. **Pendamping model.** Hire/train 5–10 "TAUT Champions" — young local tech-savvy people (mahasiswa, fresh graduate) who spend 1–2 weeks per bank sampah doing data migration (scanning old paper records into the app) and training. Without this human bridge, adoption will stall.

3. **Start with SELF-INTEREST, not data.** The pitch to operators: "Use this app to automatically generate your monthly report to DLH in 5 minutes instead of 3 hours." The data pitch (for pemda) is secondary.

4. **Pengepul as Trojan Horse.** If 2–3 large pengepul in a city say "we only buy from bank sampah that use TAUT" — adoption becomes mandatory, not optional. Negotiate with 1 major pengepul per target city BEFORE launching. Give them exclusive early access to supply data.

5. **DLH mandate is a double-edged sword.** If DLH requires all bank sampah to submit digital reports, TAUT becomes the tool — but resentment builds if operators feel forced. Better: get DLH to "recommend" TAUT and offer incentives (recognition, small grants) for early adopters.

**Realistic first 6 months target:** 200–500 bank sampah, not 1,000+. Quality over quantity. Deep adoption in 1–2 cities first, then expand.

---

### 5. Revenue Model — Can This Be Sustainable?

**The proposal's revenue model is weak and undeveloped.**

What's proposed: "Model komisi kecil dari volume transaksi (0.5–1%); potensi CSR/ESG funding dari korporasi."

**My assessment:** Commission on transaction volume is the right idea but the wrong starting point.

**Why 0.5–1% commission won't work early:**
- At 200 bank sampah averaging maybe Rp50 juta transaction volume/month total, 1% = Rp500k/month. Not sustainable.
- Margins in the waste sector are thin. Adding a commission layer creates resistance.
- Tracking "volume transaksi" requires all transactions to flow through the platform — which won't happen until the network is mature.

**A more realistic revenue model:**

1. **Tier 1 (Year 1–2): Grants and CSR.** This is not a revenue model but it's survival. Target: Danone (Aqua), Unilever, Nestlé — all have plastic waste reduction commitments and need verifiable data. They will pay for TAUT's data reports. Pitch: "Prove your plastic collection numbers with real, auditable data." Also: UNDP, World Bank (ocean plastic programs), ADB.

2. **Tier 2 (Year 2–3): Data-as-a-Service to pemda.** Charge DLH per kabupaten/kota for access to the aggregate data dashboard (Rp50–100 juta/tahun per kota). Pemda need this data for regulatory compliance. This is the most sustainable revenue source.

3. **Tier 3 (Year 3+): Premium features for pengepul/pabrik.** Paid tier for large pengepul: priority listing, advanced analytics, supply quality tracking. Commission on large B2B transactions (when volume justifies it — 0.5% on an established network is real money).

**The revenue model MUST be clarified before proceeding past Fase 1. The product cannot survive on grants alone.**

---

### 6. Risks the Proposer Missed

| Risk | Severity | Why It Matters |
|------|----------|----------------|
| **Shared-device UX failure** | HIGH | Many bank sampah have ONE phone for the whole operation. The app assumes one user = one device. Multi-operator session management (shift-based login, kiosk mode) is not in the requirements. |
| **Fraud/gaming the poin system** | HIGH | Nasabah or operators could collude to generate fake transactions to earn points. Without verification (weight photo? operator integrity?), the poin system becomes a leaky bucket. |
| **Pengepul cartel resistance** | HIGH | The proposal mentions this but understates it. In many cities, 2–3 large pengepul control the market. They have ZERO incentive to join a transparent pricing system. They may actively block TAUT by penalizing bank sampah that use it. |
| **Regulatory risk: UU PDP** | MEDIUM | The proposal mentions compliance but storing location + transaction data of millions of nasabah requires serious data protection infrastructure. A breach would kill trust immediately. |
| **Operator phone/storage constraints** | MEDIUM | Rp500k Android phones typically have 8–16GB storage and 1–2GB RAM. The app must be aggressively lean. QR receipt images alone could fill storage quickly. Auto-cleanup policies needed. |
| **Power/battery dependency** | MEDIUM | Many bank sampah operate in areas with intermittent electricity. App must handle sudden shutdowns without data loss. Not mentioned. |
| **Poin devaluation/spam** | LOW-MEDIUM | If too many nasabah accumulate points without enough redemption partners, frustration builds. Need to ensure partner merchants before launching poin redemption. |
| **Key-person dependency** | MEDIUM | Bank sampah operators often have ONE person who knows the system. If they leave or are sick, the digital transition stalls. Training redundancy is critical. |
| **Competition from super-apps** | LOW-MEDIUM | Gojek, Grab, or Shopee could add "waste collection" as a feature. Their reach dwarfs TAUT's. The moat is offline-first + deep waste-sector domain knowledge. |
| **Hardware failure / phone theft** | LOW | Bank sampah phone gets stolen/lost — all transaction records gone if sync is stale. Need offline backup strategy (share QR codes as backup? local microSD backup?). |

---

### 7. What MUST Change Before We Proceed

**Non-negotiable changes:**

1. **Restructure MVP.** Remove harga acuan from MVP. Add poin/nasabah reward system to Fase 1 (just after MVP validation). The incentive chain must start with nasabah, not end with pemda.

2. **Define user acquisition plan concretely.** Name the first 3 target cities/districts. Identify at least 2 early-adopter bank sampah per city before writing another line of code. Identify 1 pengepul partner willing to pilot. The answer "we'll figure it out during development" is not acceptable.

3. **Revenue model: commit to primary source.** If grants/CSR is the survival revenue, budget the grant-writing effort (it takes 6–12 months to secure grants). If pemda data subscription is the goal, get a letter of intent from 1 DLH before building the dashboard.

4. **Add shared-device/kiosk mode to MVP requirements.** Without this, the app fails in its primary deployment scenario.

5. **Field validation before Fase 1 build.** Spend 2 weeks visiting 5–10 bank sampah. Watch them use their current system. Ask: "What would make you switch?" Do NOT assume the answer is "a digital app." The real answer might be "a consistent buyer for our sorted waste" or "someone to pick up heavy items." Build based on what you hear, not what you assumed.

6. **Anti-fraud mechanism design before poin launch.** Even a simple rule (max X kg per household per week; weight photo required above threshold) prevents the most obvious gaming.

7. **Name the exit/continuation criteria.** What specific metric triggers a "go" vs. "no-go" decision after MVP? (e.g., "After 3 months with 20 bank sampah: if <30% are active weekly users → pivot or kill.")

---

### 8. Summary Judgment

**Likelihood of success with current proposal:** 30%

**Likelihood with changes above:** 65%

**The thesis (digital waste chain for Indonesia's informal recycling sector) is fundamentally sound. The execution plan is what needs work.** The proposal reads like a technologist's solution (build the app, they will come) rather than a PM's solution (understand the adoption path, build the incentive chain, de-risk the cold-start problem).

**My vote:** Conditional proceed. Accept the problem, accept the target users, accept the offline-first architecture. But RESTRUCTURE the phasing, DEVELOP a real acquisition plan, and COMMIT to a revenue model before we commit engineering resources to Fase 2 features.

---

*PM Review by Product Manager*
*23 Juni 2026*
