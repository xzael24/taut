# TAUT System Architecture

> Version: 1.0.0  
> Last updated: 2026-06-25

## Architectural Overview

TAUT follows a **Clean Architecture** pattern with **offline-first** principles. The system is divided into three primary components: the Android client application, the backend server, and shared protobuf definitions.

```
┌─────────────────────────────────────────────────────────────────────────┐
│                           TAUT SYSTEM                                   │
├─────────────────────────────────────────────────────────────────────────┤
│                                                                          │
│  ┌─────────────────────────────┐    ┌──────────────────────────────┐   │
│  │      ANDROID CLIENT         │    │        BACKEND SERVER         │   │
│  │   (Kotlin / Jetpack Compose)│    │     (Kotlin / Ktor / gRPC)     │   │
│  │                             │    │                              │   │
│  │  ┌───────────────────────┐  │    │  ┌────────────────────────┐  │   │
│  │  │     UI Layer          │  │    │  │    HTTP Routes         │  │   │
│  │  │  (Compose Screens)    │  │    │  │  (Auth, Transactions,  │  │   │
│  │  │  + ViewModels         │  │    │  │   Catalog, Dashboard,  │  │   │
│  │  └──────────┬────────────┘  │    │  │   Device, Sync, SMS)   │  │   │
│  │             │               │    │  └──────────┬─────────────┘  │   │
│  │  ┌──────────▼────────────┐  │    │             │                │   │
│  │  │     Domain Layer      │  │    │  ┌──────────▼─────────────┐  │   │
│  │  │  (Use Cases, Models)  │  │    │  │    Service Layer       │  │   │
│  │  └──────────┬────────────┘  │    │  │  (Auth, Transaction,   │  │   │
│  │             │               │    │  │   Catalog, Dashboard,  │  │   │
│  │  ┌──────────▼────────────┐  │    │  │   Device, Sync, SMS)   │  │   │
│  │  │     Data Layer        │  │    │  └──────────┬─────────────┘  │   │
│  │  │                       │  │    │             │                │   │
│  │  │  ┌─────────────────┐  │  │    │  ┌──────────▼─────────────┐  │   │
│  │  │  │  Room + SQLCipher│  │  │    │  │     Data Layer        │  │   │
│  │  │  │  (Offline Store) │  │  │    │  │  (PostgreSQL + Redis)  │  │   │
│  │  │  └────────┬─────────┘  │  │    │  └────────────────────────┘  │   │
│  │  │           │            │  │    │                              │   │
│  │  │  ┌────────▼─────────┐  │  │    │  ┌────────────────────────┐  │   │
│  │  │  │  gRPC Client     │  │  │    │  │  gRPC Server          │  │   │
│  │  │  │  (Bidi Streaming) │  │  │    │  │  (Sync Service)       │  │   │
│  │  │  └──────────────────┘  │  │    │  └────────────────────────┘  │   │
│  │  └─────────────────────────┘  │    └──────────────────────────────┘   │
│  │                               │                                       │
│  └──────────┬────────────────────┘    ┌──────────────────────────────┐   │
│             │           ▲             │   SHARED PROTO              │   │
│             │   HTTPS   │   gRPC     │                              │   │
│             └───────────┼────────────>│  auth_service.proto         │   │
│                         │             │  transaction_service.proto  │   │
│                         │             │  catalog_service.proto      │   │
│                         │             │  dashboard_service.proto    │   │
│                         │             │  sync_service.proto         │   │
│                         │             │  common.proto               │   │
│                         │             │  models.proto               │   │
│                         │             └──────────────────────────────┘   │
│                                                                          │
└─────────────────────────────────────────────────────────────────────────┘
```

---

## Component Overview

### 1. Backend (Ktor HTTP Server + gRPC Server)

The backend is built with **Kotlin 2.1.20** and **Ktor 2.3.13**, serving both HTTP REST APIs and gRPC endpoints on port `9000`.

#### HTTP Routes (`backend/src/main/kotlin/com/taut/routes/`)

| Route File | Base Path | Description |
|---|---|---|
| `AuthRoutes.kt` | `/v1/auth/*` | OTP request/verify, token refresh, PIN change/verify |
| `TransactionRoutes.kt` | `/v1/transactions/*` | CRUD for transactions, void, customer history |
| `CatalogRoutes.kt` | `/v1/categories/*`, `/v1/prices` | Waste categories, price catalog, price history |
| `DashboardRoutes.kt` | `/v1/dashboard/*` | Operator stats, bank stats |
| `DeviceRoutes.kt` | `/v1/devices/*` | Device registration and management |
| `SyncRoutes.kt` | `/v1/sync/*` | Data sync operations |
| `SmsRoutes.kt` | `/v1/sms/*` | SMS communication |
| `ComplianceRoutes.kt` | `/v1/compliance/*` | Regulatory compliance endpoints |

#### Services (`backend/src/main/kotlin/com/taut/service/`)

| Service | Responsibility |
|---|---|
| `AuthService.kt` | OTP generation/verification, JWT token management, PIN handling |
| `TransactionService.kt` | Transaction CRUD, void operations, idempotency |
| `CatalogService.kt` | Waste categories, price catalogs, price history |
| `DashboardService.kt` | Aggregated statistics for operators and banks |
| `DeviceService.kt` | Device registration, binding, key management |
| `SmsService.kt` | SMS OTP delivery via third-party provider |
| `SyncServiceImpl.kt` | gRPC bidi-streaming sync implementation |

#### Plugins (`backend/src/main/kotlin/com/taut/plugins/`)

| Plugin | Purpose |
|---|---|
| `Security.kt` | JWT authentication, CORS, content negotiation |
| `Routing.kt` | Route grouping and registration |
| `Serialization.kt` | JSON (kotlinx.serialization) |
| `StatusPages.kt` | Error handling and status page responses |
| `CallLogging.kt` | Request/response logging |

#### Database Layer (`backend/src/main/kotlin/com/taut/db/`)

| File | Purpose |
|---|---|
| `Database.kt` | Database initialization and connection management |
| `Tables.kt` | Table definitions (DDL) |
| `Jdbc.kt` | JDBC utility and query helpers |

### 2. Android Client (Room DB + WorkManager + gRPC Client)

The Android app uses **Clean Architecture with MVVM** pattern, **Hilt** for DI, and **Jetpack Compose** for UI.

#### Data Layer (`android/app/src/main/java/com/taut/app/data/`)

| Component | Description |
|---|---|
| `local/entity/Entities.kt` | Room entity definitions (TransactionEntity, etc.) |
| `local/dao/Daos.kt` | Room DAO interfaces |
| `local/TautDatabase.kt` | Room database with SQLCipher support |
| `local/converter/TautConverters.kt` | Type converters for Room |
| `sync/SyncWorker.kt` | WorkManager-based background sync worker |
| `sync/GrpcClientProvider.kt` | gRPC client initialization and lifecycle |
| `sync/SyncMetadataStore.kt` | Sync state tracking and metadata |
| `repository/TransactionRepository.kt` | Transaction data operations |
| `repository/CustomerRepository.kt` | Customer data operations |
| `repository/WasteCategoryRepository.kt` | Waste category data operations |
| `repository/OperatorRepository.kt` | Operator auth and profile operations |
| `repository/SmsQueueRepository.kt` | SMS queue management |

#### UI Layer (`android/app/src/main/java/com/taut/app/ui/`)

| Screen | ViewModel | Purpose |
|---|---|---|
| `screens/auth/` | `PinEntryViewModel` | PIN-based authentication |
| `screens/home/` | `HomeViewModel` | Dashboard and navigation |
| `screens/weigh/` | `WeighViewModel` | Waste weighing transaction input |
| `screens/history/` | `HistoryViewModel` | Transaction history |
| `screens/prices/` | `PriceListViewModel` | Current price catalog |
| `screens/settings/` | `SettingsViewModel` | User and app settings |
| `screens/sync/` | `SyncViewModel` | Sync status and controls |

#### Dependency Injection (`android/app/src/main/java/com/taut/app/di/`)

| Module | Provides |
|---|---|
| `DatabaseModule.kt` | Room database, DAOs |
| `NetworkModule.kt` | gRPC channel, stubs |

#### Utilities (`android/app/src/main/java/com/taut/app/util/`)

| Utility | Purpose |
|---|---|
| `CryptoManager.kt` | Keystore operations, encryption/decryption |
| `AudioManager.kt` | Text-to-speech and audio feedback |
| `NetworkMonitor.kt` | Connectivity change observation |
| `SyncScheduler.kt` | WorkManager sync scheduling |

### 3. Proto Definitions (`proto/`)

Shared Protocol Buffer definitions for API communication:

| File | Services | Messages |
|---|---|---|
| `common.proto` | — | Enums, common types, pagination |
| `models.proto` | — | Domain models (Transaction, Customer, etc.) |
| `auth_service.proto` | AuthService | Login, token refresh, PIN operations |
| `transaction_service.proto` | TransactionService | CRUD, void, history |
| `catalog_service.proto` | CatalogService | Categories, prices |
| `dashboard_service.proto` | DashboardService | Statistics, summaries |
| `sync_service.proto` | SyncService | Bidi-streaming sync |

---

## Data Flow

### Online Path

```
Android App                          Backend Server
───────────                          ──────────────
                                          
  User Action ──HTTP POST──>   Route Handler ──> Service ──> PostgreSQL
                                        │                        │
  Response  <──HTTP JSON───   Route Handler <── Service <─────────┘
                                        │
  gRPC Sync <──bidi stream───       gRPC Server                   
```

1. User performs an action (e.g., creates a transaction)
2. HTTP request is sent to Ktor server
3. Route handler validates and delegates to service layer
4. Service processes request against PostgreSQL
5. Response is returned as JSON
6. gRPC bidirectional streaming sync picks up changes for other clients

### Offline Path

```
Android App
───────────
                                          
  User Action ──> Write to Room (SQLCipher encrypted)
                      │
                      ▼
              Queue in WorkManager
                      │
                      │   [Connectivity restored]
                      ▼
              gRPC SyncWorker ──bidi stream──> Backend gRPC Server
                      │                                │
                      ▼                                ▼
              Mark synced                      Process & persist
              Remove from queue                Return confirmation
```

1. User performs an action while offline
2. Data is stored in local Room database (encrypted with SQLCipher)
3. Operation is queued via WorkManager
4. When connectivity is restored, `SyncWorker` executes
5. gRPC bidirectional streaming sends pending changes to server
6. Server processes and returns confirmation
7. Local data is marked as synced

---

## Database Schema

### Tables (`backend/src/main/kotlin/com/taut/db/Tables.kt`)

The backend defines the following tables:

| Table | Description | Key Columns |
|---|---|---|
| `operators` | Waste bank operators | id, phone_number, name, bank_sampah_id, pin_hash, status |
| `bank_sampah` | Waste bank organizations | id, name, address, region_id, status |
| `customers` | Waste depositor customers | id, name, phone, address, balance |
| `transactions` | Weight/transaction records | id, operator_id, customer_id, bank_id, type, status, total, created_at |
| `transaction_items` | Individual waste items in a tx | id, transaction_id, category_id, weight_kg, price_per_kg, subtotal |
| `waste_categories` | Waste type categories | id, name, unit, description, status |
| `price_catalog` | Current prices per category | id, category_id, region_id, price_per_kg, effective_date, version |
| `devices` | Registered Android devices | id, operator_id, device_id, public_key, last_seen, status |
| `otp_codes` | One-time password records | id, session_id, phone, code, expires_at, verified, attempts |
| `sync_log` | Sync audit trail | id, device_id, last_sync, status, record_count |

### Entity Relationships

```
operators ──> bank_sampah      (M:1)
operators ──> devices          (1:M)
operators ──> transactions     (1:M)
customers ──> transactions     (1:M)
transactions ──> transaction_items (1:M)
transaction_items ──> waste_categories (M:1)
waste_categories ──> price_catalog (1:M)
regions ──> bank_sampah        (1:M)
regions ──> price_catalog      (1:M)
```

### Migration Strategy

- **Schema migrations** are handled programmatically via DDL scripts in `Database.kt`
- **Version tracking** via a `schema_version` table
- **Rolling upgrades** with backward-compatible schema changes
- **TimescaleDB** hybertables for `transactions` time-series partitioning

---

## Security Architecture

### JWT Authentication

```
┌─────────────────────────────────────────────────────────────┐
│                    JWT Token Flow                            │
├─────────────────────────────────────────────────────────────┤
│                                                              │
│ 1. OTP Request ──> SMS sent to phone                         │
│ 2. OTP Verify  ──> Returns {access_token, refresh_token}     │
│ 3. Access Token ──> Short-lived (15 min), device-bound      │
│ 4. Refresh Token ─> Long-lived (30 days), rotation on use   │
│ 5. Token Refresh ─> Validates device signature + token      │
│                                                              │
│ Payload: { sub, operator_id, bank_id, device_id, iat, exp } │
│                                                              │
└─────────────────────────────────────────────────────────────┘
```

### Encryption at Rest

| Component | Algorithm | Key Management |
|---|---|---|
| SQLCipher (Room DB) | AES-256-GCM | Derived from user PIN + device keystore |
| Android Keystore | AES-256 | Hardware-backed storage |
| Server DB | PostgreSQL TDE | Managed via cloud provider |

### PIN Security

- **Storage**: Argon2id hash (server-side)
- **Verification**: Server-side with rate limiting (max 5 attempts)
- **Lockout**: 30-minute lockout after 5 failed attempts
- **Rotation**: Mandatory every 90 days
- **Idempotency**: PIN changes require idempotency key

### Device Binding

- Each device generates a unique RSA keypair
- Public key is registered during OTP verification
- Device signature is verified on token refresh
- Device ID is bound to all transactions

---

## Component Interaction Diagram

```
┌─────────────────────────────────────────────────────────────────┐
│                       TAUT DEPLOYMENT                           │
├─────────────────────────────────────────────────────────────────┤
│                                                                  │
│  ┌──────────┐     ┌───────────┐     ┌──────────┐              │
│  │ Android  │────>│ Envoy/Kong│────>│  Ktor    │              │
│  │ Clients  │     │ API Gw    │     │  Server  │              │
│  │ (N+1)    │<────│           │<────│  :9000   │              │
│  └──────────┘     └───────────┘     └────┬─────┘              │
│                                          │                      │
│  ┌───────────────────────────────────────┼──────────────────┐   │
│  │              DATA LAYER               │                  │   │
│  │                                       ▼                  │   │
│  │  ┌──────────┐  ┌────────────┐  ┌──────────────┐        │   │
│  │  │PostgreSQL│  │   Redis    │  │ gRPC Server  │        │   │
│  │  │  :5432   │  │   :6379    │  │   :9000      │        │   │
│  │  └──────────┘  └────────────┘  └──────────────┘        │   │
│  │                                                         │   │
│  └─────────────────────────────────────────────────────────┘   │
│                                                                  │
│  ┌──────────────────────┐                                       │
│  │    MONITORING        │                                       │
│  │  Grafana / Loki /    │                                       │
│  │  Mimir / Prometheus  │                                       │
│  └──────────────────────┘                                       │
│                                                                  │
└─────────────────────────────────────────────────────────────────┘
```

---

## Scalability Considerations

### Horizontal Scaling
- **Ktor server**: Stateless, horizontally scalable behind API gateway
- **PostgreSQL**: Read replicas for dashboard queries
- **gRPC sync**: Connection pooling for multiple devices

### Offline-First Optimizations
- **Local-first writes**: No network dependency for core operations
- **Batch sync**: Aggregated sync payloads reduce bandwidth
- **Delta sync**: Only changed records transmitted
- **Compression**: Protobuf binary format minimizes payload size

### Resource Constraints
- **Target**: Android Go devices with 1GB RAM
- **APK size**: ~3–4 MB
- **Memory budget**: <50MB for app runtime
- **Storage**: SQLCipher WAL mode for efficient journaling

---

## Observability

### Metrics & Monitoring
- **Application metrics**: Ktor metrics plugin (request rate, latency, errors)
- **Database metrics**: PostgreSQL pg_stat_statements, connection pools
- **Android metrics**: WorkManager success/failure rates, sync latency

### Logging
- **Structured logging**: JSON format via logback
- **Log levels**: TRACE, DEBUG, INFO, WARN, ERROR
- **Log shipping**: Loki aggregation with Grafana dashboards

### Health Checks
- **Endpoint**: `GET /health` on port 9000
- **Dependencies**: PostgreSQL connectivity, Redis connectivity
- **Docker healthcheck**: 30s interval, 10s timeout, 3 retries

---

## Deployment Architecture

See [DEPLOYMENT.md](DEPLOYMENT.md) for full deployment details.

### Environments

| Environment | Infrastructure | Purpose |
|---|---|---|
| **Local Dev** | Docker Compose | Development and testing |
| **Staging** | Docker Compose | Integration and QA |
| **Production** | Docker + Kubernetes (planned) | Live service |

### Containerization

- **Multi-stage Dockerfile**: Build (JDK 21) → Runtime (JRE 21)
- **Fat JAR**: `backend-all.jar` with all dependencies
- **Non-root user**: Runs as `taut` user (UID 1001)
- **Health check**: `curl -f http://localhost:9000/health`
