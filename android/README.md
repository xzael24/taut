# TAUT — Android App Project

## Overview

TAUT (Platform Daur Ulang Digital) — Android app for bank sampah operators to record waste deposits offline-first, with sync to cloud backend.

**Target devices:** Android Go (1GB RAM, Android 8.0+)
**Min API:** 26 | **Target API:** 34
**Language:** Kotlin | **UI:** Jetpack Compose
**Local DB:** Room + SQLCipher (AES-256-GCM)
**DI:** Hilt | **Sync:** WorkManager + gRPC

---

## Project Structure

```
android/
├── app/
│   ├── src/main/
│   │   ├── java/com/taut/app/
│   │   │   ├── TautApplication.kt          # Hilt Application, TTS init
│   │   │   ├── MainActivity.kt             # Single Activity, Compose
│   │   │   ├── di/                         # Hilt modules
│   │   │   │   ├── DatabaseModule.kt
│   │   │   │   └── NetworkModule.kt
│   │   │   ├── data/local/                 # Room database
│   │   │   │   ├── entity/                 # Entities (UUIDv7, Long money)
│   │   │   │   ├── dao/                    # DAOs (Flow + suspend)
│   │   │   │   ├── converter/              # TypeConverters
│   │   │   │   └── TautDatabase.kt         # SQLCipher encrypted DB
│   │   │   ├── ui/
│   │   │   │   ├── theme/                  # Design tokens (dark mode default)
│   │   │   │   │   ├── Color.kt
│   │   │   │   │   ├── Type.kt
│   │   │   │   │   ├── DesignTokens.kt
│   │   │   │   │   └── Theme.kt
│   │   │   │   ├── navigation/             # NavHost + Screen routes
│   │   │   │   │   ├── Screen.kt
│   │   │   │   │   └── NavGraph.kt
│   │   │   │   ├── screens/                # Screen composables
│   │   │   │   │   ├── home/
│   │   │   │   │   │   ├── SplashScreen.kt
│   │   │   │   │   │   └── HomeScreen.kt
│   │   │   │   │   ├── auth/
│   │   │   │   │   │   └── PinEntryScreen.kt
│   │   │   │   │   ├── weigh/
│   │   │   │   │   │   ├── WeightEntryScreen.kt      # Step 1
│   │   │   │   │   │   ├── CategorySelectionScreen.kt # Step 2
│   │   │   │   │   │   ├── ConfirmationScreen.kt     # Step 3
│   │   │   │   │   │   └── TransactionSavedScreen.kt
│   │   │   │   │   ├── prices/
│   │   │   │   │   │   └── PriceListScreen.kt
│   │   │   │   │   ├── history/
│   │   │   │   │   │   └── HistoryScreen.kt
│   │   │   │   │   └── settings/
│   │   │   │   │       └── SettingsScreen.kt
│   │   │   │   └── components/             # Reusable components
│   │   │   └── util/                       # Utilities
│   │   │       ├── NetworkMonitor.kt
│   │   │       ├── CryptoManager.kt
│   │   │       └── AudioManager.kt
│   │   └── res/
│   │       ├── values/
│   │       │   └── strings.xml             # All Bahasa Indonesia
│   │       ├── drawable/                   # Category photos (bundled)
│   │       └── raw/                        # Audio prompts (future)
│   └── build.gradle.kts
├── build.gradle.kts
├── settings.gradle.kts
├── gradle.properties
└── gradle/wrapper/gradle-wrapper.properties
```

---

## Setup

### Prerequisites
- Android Studio Ladybug (2024.2.1+) or IntelliJ IDEA
- JDK 17
- Android SDK 34

### Build
```bash
cd android
./gradlew assembleDebug
```

### Run
```bash
./gradlew installDebug
```

---

## Key Design Decisions

### 1. Dark Mode Default (§1.3)
```kotlin
// Theme.kt — defaults to dark
@Composable
fun TautTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),  // System default; app prefers dark
    content: @Composable () -> Unit
)
```

### 2. Integer-Only Money (satuan rupiah)
All monetary values stored as `Long` (cents). No floats.
```kotlin
// TransactionEntity
@ColumnInfo(name = "total_value") val totalValue: Long  // Rp15.000 = 1500000
```

### 3. UUIDv7 for Offline IDs
Primary keys are UUIDv7 strings assigned on device before sync.
```kotlin
// Entities.kt
@PrimaryKey val id: String  // UUIDv7
```

### 4. Touch Targets ≥ 56dp (WCAG 2.1 AA+)
```kotlin
// DesignTokens.kt
val minTouchTarget = 56.dp  // Exceeds 44dp minimum
```

### 5. Typography Scale (§3.2)
System font only (Roboto/Noto). No custom fonts — smallest APK, best 1GB RAM perf.

| Role | Size | Weight |
|------|------|--------|
| Title XL | 28sp | Bold |
| Title L | 24sp | Bold |
| Subtitle | 18sp | Medium |
| Body L | 18sp | Regular |
| Label | 14sp | Medium |
| Caption | 12sp | Regular |
| Number XL | 36sp | Bold |
| Number L | 24sp | Bold |

### 6. Spacing Base Unit: 8dp (§5.2)
All spacing multiples of 8dp: 4, 8, 12, 16, 24, 32, 48.

### 7. Screen Layout Rules (§5.5)
- Content never touches edges (24dp margins)
- Vertical scroll only
- Sticky total row during transactions
- Fixed bottom bar with primary action

---

## Transaction Flow (3 Steps)

```
HOME → [⚖️ Timbang] 
    → Step 1: WeightEntry (nasabah, photo optional, weight)
    → Step 2: CategorySelection (2-col photo grid + search)
    → Step 3: Confirmation (details + total in green 36sp)
    → Saved Overlay → [Timbang Lagi] or [Ke Beranda]
```

Each step has step indicator at bottom: `[1/3: Timbang]`, `[2/3: Pilih]`, `[3/3: Simpan]`

---

## Kiosk / Shared Device

PIN entry screen with custom keypad (§6.6):
- 4 digits only
- 72×64dp keys (large for gloves/wet hands)
- Offline bcrypt verification

---

## Local Database (Room + SQLCipher)

| Table | Purpose |
|-------|---------|
| `waste_categories` | Cached categories with local photo paths |
| `customers` | Nasabah lookup (name, phone, address) |
| `transactions` | Core business records (pending_sync → synced) |
| `transaction_items` | Line items per category |
| `operator_profiles` | PIN-authenticated operators per device |
| `sms_queue` | Offline SMS delivery queue |
| `devices` | Device registration for sync identity |
| `sync_log` | Audit trail of sync sessions |
| `user_consent_log` | UU PDP compliance |

---

## Sync Architecture (Deferred)

- WorkManager periodic: 15min WiFi, 4h cellular
- gRPC bidirectional stream
- Lamport clock for ordering
- Conflict resolution: first-write-wins for transactions

---

## Accessibility (WCAG AA + TAUT-specific)

- Min text: 12sp (default body: 18sp)
- Min touch: 56×56dp
- No swipe/pinch/long-press
- All colors meet 4.5:1 contrast
- Color never sole indicator (icon + text always)
- TTS offline Indonesian
- Fully functional offline

---

## APK Size Target

- **Baseline:** <15MB
- Compose + Room + Hilt + SQLCipher + Coil ≈ 4-6MB
- Category photos: 50 categories × 50KB = ~2.5MB
- Total target: ~10MB

---

## Next Steps (MVP Implementation)

1. **Wire up ViewModels** with Hilt + StateFlow
2. **Implement PIN verification** with bcrypt
3. **Build category photo assets** (real waste photos)
4. **Implement SyncWorker** with gRPC stubs
5. **Add SMS queue processing** with WorkManager
6. **Write unit/UI tests** for transaction flow
7. **Performance profile** on 1GB device (Evercoss A66, Nokia C1)

---

## Documentation References

- `docs/architecture.md` — Technical architecture
- `docs/ux-design-system.md` — Visual specification
- `docs/ux-user-flows.md` — User flow diagrams
- `docs/database-schema.md` — PostgreSQL schema (server)

---

*Generated from design specs — ready for implementation.*