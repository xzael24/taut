# TAUT Backend Service

Platform Daur Ulang Digital — Backend API service built with **Kotlin** and **Ktor**.

## Tech Stack

| Component | Technology |
|-----------|-----------|
| Language | Kotlin 1.9.x (JVM 17) |
| Framework | Ktor 2.3.x (Netty) |
| Database | PostgreSQL 15 + TimescaleDB |
| Connection Pool | HikariCP |
| Migrations | Flyway |
| Serialization | kotlinx.serialization (JSON) |
| Auth | JWT (auth0) |
| Build | Gradle Kotlin DSL |

## Project Structure

```
backend/
├── build.gradle.kts              # Gradle build config
├── settings.gradle.kts           # Gradle settings
├── gradle.properties             # Build properties
├── gradlew / gradlew.bat         # Gradle wrapper
├── src/
│   ├── main/
│   │   ├── kotlin/com/taut/
│   │   │   ├── Application.kt           # Main entry point
│   │   │   ├── config/
│   │   │   │   ├── AppConfig.kt         # App configuration (server, db, jwt)
│   │   │   │   └── DatabaseConfig.kt    # HikariCP + DataSources
│   │   │   ├── plugins/
│   │   │   │   ├── Routing.kt           # Route registration + CORS
│   │   │   │   ├── Serialization.kt     # JSON content negotiation
│   │   │   │   ├── Security.kt          # JWT authentication
│   │   │   │   ├── StatusPages.kt       # Error handling
│   │   │   │   └── CallLogging.kt       # Request logging
│   │   │   ├── routes/
│   │   │   │   ├── AuthRoutes.kt        # OTP, JWT, PIN endpoints
│   │   │   │   ├── TransactionRoutes.kt # CRUD transactions
│   │   │   │   ├── CatalogRoutes.kt     # Waste categories + prices
│   │   │   │   ├── DashboardRoutes.kt   # Operator + DLH dashboards
│   │   │   │   ├── DeviceRoutes.kt      # Device registration
│   │   │   │   ├── ComplianceRoutes.kt  # UU PDP compliance
│   │   │   │   ├── SmsRoutes.kt         # SMS queue management
│   │   │   │   └── SyncRoutes.kt        # gRPC sync placeholder
│   │   │   ├── models/
│   │   │   │   └── Models.kt            # All data models
│   │   │   └── service/
│   │   │       ├── AuthService.kt       # Auth business logic
│   │   │       ├── TransactionService.kt# Transaction business logic
│   │   │       ├── CatalogService.kt    # Catalog business logic
│   │   │       ├── DashboardService.kt  # Dashboard business logic
│   │   │       ├── DeviceService.kt     # Device management logic
│   │   │       └── SmsService.kt        # SMS queuing & delivery
│   │   └── resources/
│   │       ├── application.conf         # Server configuration
│   │       └── db/migration/
│   │           ├── V1_0_0__initial_schema.sql
│   │           ├── V1_0_1__seed_reference_data.sql
│   │           ├── V1_0_2__add_price_references.sql
│   │           └── V1_0_3__add_audit_log.sql
│   └── test/kotlin/com/taut/           # Test stubs (TBD)
└── README.md
```

## Prerequisites

- **JDK 17+** (OpenJDK or Adoptium recommended)
- **PostgreSQL 15+** with TimescaleDB extension (optional for MVP)
- **Gradle 8.x** (or use the provided wrapper)

## Quick Start

You can run the backend **with Docker** (recommended) or **locally** with Gradle.

### Option A — Docker (Recommended)

No local JDK, PostgreSQL, or Redis needed — everything runs in containers.

```bash
docker compose -f ../docker/docker-compose.yml up -d
```

This starts PostgreSQL 16, Redis 7, pgAdmin, and the backend service.
Flyway migrations run automatically on startup.

Verify:

```bash
curl http://localhost:8080/health
# {"status":"ok","service":"taut-backend"}
```

> **Tip:** Use `make docker-up` from the project root as a shortcut.

### Option B — Local Development

#### 1. Database Setup

```bash
# Create the database
createdb taut

# Or via psql
psql -U postgres -c "CREATE DATABASE taut;"
psql -U postgres -c "CREATE USER taut WITH PASSWORD 'taut';"
psql -U postgres -c "GRANT ALL PRIVILEGES ON DATABASE taut TO taut;"
```

#### 2. Environment Variables (Optional)

The server loads defaults from `application.conf` which can be overridden by environment variables:

| Variable | Default | Description |
|----------|---------|-------------|
| `TAUT_HOST` | `0.0.0.0` | Server listen address |
| `TAUT_PORT` | `8080` | Server port |
| `TAUT_DB_URL` | `jdbc:postgresql://localhost:5432/taut` | Database JDBC URL |
| `TAUT_DB_USER` | `taut` | Database user |
| `TAUT_DB_PASSWORD` | `taut` | Database password |
| `TAUT_JWT_SECRET` | `change-me-in-production` | JWT signing secret |

#### 3. Run

```bash
# Using Gradle wrapper
./gradlew run

# Or build and run
./gradlew build
java -jar build/libs/taut-backend-0.1.0-all.jar
```

The server starts at **http://localhost:8080**. Flyway migrations run automatically on startup.

#### 4. Verify

```bash
curl http://localhost:8080/health
# {"status":"ok","service":"taut-backend"}
```

## API Endpoints

All REST endpoints are under `/v1/`. See [docs/api-spec.md](../docs/api-spec.md) for full documentation.

### Auth
| Method | Path | Description |
|--------|------|-------------|
| POST | `/v1/auth/otp/request` | Request SMS OTP |
| POST | `/v1/auth/otp/verify` | Verify OTP + get JWT |
| POST | `/v1/auth/token/refresh` | Refresh access token |
| POST | `/v1/auth/pin/change` | Change operator PIN |
| POST | `/v1/auth/pin/verify` | Verify PIN server-side |

### Transactions
| Method | Path | Description |
|--------|------|-------------|
| GET | `/v1/banks/{bank_id}/transactions` | List transactions |
| GET | `/v1/transactions/{id}` | Get single transaction |
| POST | `/v1/transactions` | Create transaction |
| POST | `/v1/transactions/{id}/void` | Void transaction (admin) |
| GET | `/v1/customers/{id}/transactions` | Customer history |

### Catalog
| Method | Path | Description |
|--------|------|-------------|
| GET | `/v1/categories` | List waste categories |
| GET | `/v1/prices` | Get price catalog |
| GET | `/v1/categories/{id}/prices/history` | Price history |

### Dashboard
| Method | Path | Description |
|--------|------|-------------|
| GET | `/v1/dashboard/operator/{bank_id}` | Operator dashboard |
| GET | `/v1/dashboard/dlh/{kota_id}` | DLH dashboard |
| GET | `/v1/dashboard/operator/{bank_id}/report` | Monthly report |

### Devices
| Method | Path | Description |
|--------|------|-------------|
| POST | `/v1/devices/register` | Register device |
| GET | `/v1/devices` | List devices |
| POST | `/v1/devices/{id}/wipe` | Remote wipe device |

### Compliance (UU PDP)
| Method | Path | Description |
|--------|------|-------------|
| GET | `/v1/users/{id}/export` | Export user data (Pasal 26) |
| POST | `/v1/users/{id}/forget` | Right to be forgotten |
| GET | `/v1/consent` | Get consent status |
| POST | `/v1/consent` | Update consent |

### SMS Admin
| Method | Path | Description |
|--------|------|-------------|
| GET | `/v1/sms/queue` | List SMS queue |
| POST | `/v1/sms/queue/{id}/retry` | Retry failed SMS |

### Sync (gRPC placeholder)
| Method | Path | Description |
|--------|------|-------------|
| GET | `/v1/sync/status` | Get sync status |
| POST | `/v1/sync/push` | One-shot sync push |

## Development

### Adding a migration

1. Create a new file in `src/main/resources/db/migration/`
2. Name format: `V{version}__{description}.sql`
3. Flyway applies migrations automatically on startup

### Running tests

```bash
./gradlew test
```

## Deployment

1. Build the fat JAR:
   ```bash
   ./gradlew build
   ```
2. Set production environment variables
3. Run with:
   ```bash
   java -jar build/libs/taut-backend-0.1.0-all.jar
   ```

For production, we recommend containerizing with Docker and deploying to GKE.
