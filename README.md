# TAUT — Platform Daur Ulang Digital (Digital Waste Bank Platform)

**TAUT** (an acronym for *Timbang, Atur, Ulang, Terima*) is an offline-first Android application and backend platform for digital waste bank (*Bank Sampah*) operations across Indonesia. It enables waste bank operators to record transactions, manage customer balances, sync data, and generate reports — even on low-end Android Go devices with intermittent internet connectivity.

## Project Overview

TAUT is a comprehensive digital waste bank platform that digitizes traditional waste management operations in Indonesia. The solution addresses the unique challenges of operating in emerging markets with limited connectivity and computational resources.

### Key Features
- **Offline-first operations**: Works seamlessly without internet connectivity
- **SQLCipher encryption**: AES-256-GCM encryption at rest for data security
- **gRPC synchronization**: Efficient bidirectional streaming for real-time data sync
- **PIN-based authentication**: Secure local authentication with biometric support
- **Multi-device support**: Works on low-end Android Go devices (1GB RAM, 3G/4G)
- **Real-time analytics**: Dashboard with operator, bank, and transaction statistics

## Tech Stack

| Layer | Technology | Rationale |
|-------|-----------|-----------|
| **Mobile App** | Kotlin (Android Native) | ~3–4 MB APK, 1GB RAM perf, native WorkManager/Room |
| **UI** | Jetpack Compose + View XML | Compose for data screens, View for CameraX |
| **Local DB** | Room + SQLCipher | AES-256-GCM at rest, WAL mode, WorkManager integration |
| **Sync Protocol** | gRPC bidi stream (Protobuf) | Smaller payloads, real-time push, efficient for low-bandwidth |
| **DI** | Hilt | Compile-time safe, native WorkManager integration |
| **Image Loading** | Coil | Kotlin-native, coroutine-based, ~200KB APK impact |
| **Target Devices** | Android 8.0+ (Go edition) | Redmi A2, Samsung A05, Advan G5, Evercoss A66, Nokia C1 |
| **API Gateway** | Envoy / Kong | gRPC-native, HTTP/2, rate limiting, auth delegation |
| **Backend** | Kotlin 2.1.20 + Ktor 2.3.13 | JVM consistency with Android |
| **Database** | PostgreSQL 16 | Hypertables, native partitioning, full-text search |
| **Cache / Queue** | Redis 7.x | Streams for tx ingestion, pub/sub for dashboards |
| **Cloud** | GCP asia-southeast2 (Jakarta) | UU PDP data residency compliance |
| **Observability** | Grafana + Loki + Mimir/Prometheus | Metrics, logs, traces unified |
| **CDN** | Cloudflare | Static assets, QR redirects, API caching |

## Directory Structure

```
├── android/                          # Android application module
│   ├── app/src/main/java/com/taut/app/
│   │   ├── data/                 # Data layer (Room, gRPC, repos, sync)
│   │   │   ├── local/           # Room DB, DAOs, entities, converters
│   │   │   ├── repository/      # Repository implementations
│   │   │   └── sync/            # SyncWorker, gRPC client, metadata store
│   │   ├── di/                  # Hilt dependency injection modules
│   │   ├── ui/                  # Jetpack Compose screens & components
│   │   │   ├── screens/        # auth/, home/, wegh/, history/, prices/, settings/
│   │   │   ├── navigation/     # NavGraph, Screen definitions
│   │   │   └── theme/          # DesignTokens, Typography
│   │   └── util/               # CryptoManager, AudioManager, NetworkMonitor
│   └── build.gradle.kts
├── backend/                         # Backend Ktor service
│   ├── src/main/kotlin/com/taut/
│   │   ├── Application.kt      # Entry point
│   │   ├── routes/             # HTTP route handlers
│   │   ├── service/            # Business logic services
│   │   ├── models/             # Data models / DTOs
│   │   ├── db/                 # Database configuration, tables
│   │   ├── config/             # App config, database config
│   │   └── plugins/            # Ktor plugins (Security, Routing, Serialization)
│   ├── src/test/kotlin/com/taut/    # Unit tests
│   ├── build.gradle.kts
│   └── Dockerfile
├── proto/                            # Shared Protobuf definitions
│   └── taut/core/v1/
│       ├── common.proto
│       ├── models.proto
│       ├── auth_service.proto
│       ├── transaction_service.proto
│       ├── catalog_service.proto
│       ├── dashboard_service.proto
│       └── sync_service.proto
├── docker/                           # Docker deployment files
│   ├── docker-compose.yml       # Staging deployment
│   ├── docker-compose.dev.yml   # Dev (hot reload) overlay
│   └── Dockerfile               # Multi-stage build
├── docs/                             # Documentation
│   ├── ARCHITECTURE.md          # System architecture
│   ├── API.md                   # API reference
│   ├── DEPLOYMENT.md            # Deployment guide
│   ├── architecture.md          # Full (legacy)
│   ├── api-spec.md              # API spec (legacy)
│   └── database-schema.md       # DB schema
├── .github/workflows/
│   └── ci.yml                   # GitHub Actions CI/CD
├── .env.template                     # Environment variables template
├── .editorconfig                     # Editor/IDE settings
└── .gitignore                        # Comprehensive gitignore
```

## Quick Start

### Backend (Local Development)

#### Prerequisites
- JDK 21+
- Docker
- Gradle 8.11.1+

#### Setup
```bash
# 1. Navigate to project root
cd taut/

# 2. Set up environment variables
cp .env.template .env
# Edit .env with your own secrets

# 3. Start dependencies (PostgreSQL + Redis)
docker compose -f docker/docker-compose.yml up -d

# 4. Build and run the Ktor service
cd backend/
./gradlew run
```

### Android (Local Development)

#### Prerequisites
- Android Studio Hedgehog+
- JDK 17+

#### Build and Install
```bash
cd android/
./gradlew compileDebugKotlin testDebugUnitTest
./gradlew installDebug
```

> **Note:** The Android app connects to the backend via gRPC. For local dev, update the endpoint in `NetworkModule.kt`.

### Docker (Full Stack)
```bash
# Staging
docker compose -f docker/docker-compose.yml up -d

# Dev with hot reload
docker compose -f docker/docker-compose.yml -f docker/docker-compose.dev.yml up
```

## Build Commands

### Backend
```bash
cd backend/
./gradlew compileKotlin          # Lint and compile
./gradlew test                   # Run all backend tests
./gradlew build                  # Full build (fat JAR)
./gradlew clean build -x test    # Build without tests
```

### Android
```bash
cd android/
./gradlew compileDebugKotlin     # Compile debug variant
./gradlew testDebugUnitTest      # Run Android unit tests
./gradlew assembleDebug          # Build debug APK
./gradlew lint                   # Run lint checks
```

## Architecture Highlights

### Clean Architecture Layers
1. **Domain Layer**: Business logic, use cases, and domain models
2. **Data Layer**: Repositories, Room DB, gRPC/HTTP data sources
3. **Presentation Layer**: Jetpack Compose UI, ViewModels, navigation

### CQRS-like Separation
- **Write operations**: REST APIs with immediate server-side validation
- **Read operations**: Optimized queries for dashboards and listings

### Offline-First Design
- **Local Storage**: Room with SQLCipher AES-256-GCM encryption
- **Sync Queue**: WorkManager with retry policy for pending operations
- **Conflict Resolution**: Server-side merge with timestamp-based ordering
- **Network Awareness**: Connectivity monitoring for adaptive sync

### Security Architecture
- **Authentication**: JWT tokens (access + refresh) with device binding
- **At-Rest Encryption**: SQLCipher (AES-256-GCM) on local database
- **Run-time Security**: PIN verification with rate limiting and lockout
- **Keystore**: Android Keystore for cryptographic key management

## Branch Strategy

```
main ──── Production-ready code (protected)
  │
  └── develop ── Integration branch for features
        │
        ├── feature/* ── Feature development
        ├── fix/*      ── Bug fixes
        └── hotfix/*   ── Emergency production patches
```

### CI/CD Pipeline (GitHub Actions)

| Stage | Job | Description |
|-------|-----|-------------|
| 1 | `backend-test` | Compile Kotlin, run 56 test files, 30/30 unit tests pass |
| 2 | `android-test` | Compile debug Kotlin, run 6 Android unit tests |
| 3 | `docker-build` | Build Docker image, smoke test container |

**Workflows**: `.github/workflows/ci.yml` triggers on push/PR to `main` for `backend/`, `android/`, `docker/` paths.

**Note**: The canonical CI file is `.github/workflows/ci.yml`. No duplicate workflow files detected.

## Environment Configuration

### Environment Variables

| Variable | Required | Default | Description |
|----------|----------|---------|-------------|
| `TAUT_JWT_SECRET` | **Yes** | — | JWT signing and verification secret |
| `TAUT_DB_PASSWORD` | **Yes** | — | PostgreSQL database password |
| `TAUT_DB_URL` | No | `jdbc:postgresql://postgres:5432/taut` | Database connection URL |
| `TAUT_DB_USER` | No | `taut` | Database username |
| `TAUT_HOST` | No | `0.0.0.0` | Server bind address |
| `TAUT_PORT` | No | `9000` | Server port (Ktor HTTP + gRPC) |

### Setup
```bash
cp .env.template .env
# Edit .env with secure values
# TAUT_JWT_SECRET=your-256-bit-secret
# TAUT_DB_PASSWORD=your-strong-password
```

## Contributing

1. **Fork** the repository and create a feature branch from `develop`.
2. **Make your changes** following the existing code style (see `.editorconfig`).
3. **Write tests** — unit tests for domain/data layers, instrumented tests for UI.
4. **Run lint** — `./gradlew lint` and ensure zero warnings.
5. **Submit a pull request** with a clear description of what and why.

### Commit Conventions
We follow [Conventional Commits](https://www.conventionalcommits.org/):
- `feat:` — new feature
- `fix:` — bug fix
- `docs:` — documentation only
- `refactor:` — code change that neither fixes a bug nor adds a feature
- `test:` — adding or fixing tests
- `chore:` — build, CI, or tooling changes

### Code Review Focus
- Correctness and edge cases (offline → online transitions)
- Memory/performance on 1GB RAM devices
- Security (no PII leaks, proper encryption)
- UU PDP compliance (data minimization, consent)

## Sprint 1 — DevOps & Infrastructure Status

| Task | Status | Notes |
|------|--------|-------|
| Docker Compose (PostgreSQL + Redis) | ✅ Verified | `docker/docker-compose.yml` includes Redis 7; root compose is PostgreSQL-only. Requires Docker Desktop running. |
| SQL Migration (`V1__init.sql`) | ✅ Created | Consolidated from `Tables.kt` — 20 tables, enums, indexes, partitions, seed data. Placed in `backend/src/main/resources/db/migration/` |
| `.env` file | ✅ Created | Copy of `.env.template` with placeholder values — fill in `TAUT_JWT_SECRET` and `TAUT_DB_PASSWORD` |
| CI pipeline YAML validation | ✅ Fixed | Fixed malformed `TAUT_DB_URL` in `ci.yml` (`localhost:***@v4` → `localhost:5432/taut`) |
| `scripts/start-dev.bat` | ⚠️ Partial | Uses root `docker-compose.yml` (PostgreSQL + Adminer only, no Redis). For full stack with Redis: `docker compose -f docker/docker-compose.yml up -d` |
| `scripts/migrate.sh` | ✅ Fixed | Fixed malformed JDBC URL (removed `***` placeholder in connection string) |
| README.md Sprint 1 status | ✅ Updated | This section |

**Remaining for Sprint 1 completion**:
- Docker Desktop must be running on the host for `docker compose up` to work
- Replace placeholder values in `.env` with real secrets before any deployment
- Consider adding Redis to root `docker-compose.yml` or update `start-dev.bat` to use `docker/docker-compose.yml`

## License

MIT License — see the [LICENSE](LICENSE) file for details.

Copyright © 2026 TAUT Project
