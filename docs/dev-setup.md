# Developer Onboarding Guide

> Everything you need to go from zero to a running TAUT backend.

---

## Prerequisites

| Tool | Minimum Version | Install |
|------|----------------|---------|
| **JDK** | 17 | [Adoptium](https://adoptium.net/) or `sdk install java 17.0.10-tem` |
| **Docker Desktop** | 4.x | [docker.com](https://docs.docker.com/get-docker/) |
| **Docker Compose** | v2+ | Ships with Docker Desktop ≥ 4.x |
| **Git** | 2.x | [git-scm.com](https://git-scm.com/) |
| **Make** | 4.x | Usually pre-installed; on Windows use `winget install GnuWin32.Make` |

Optional (recommended):
- **IntelliJ IDEA** (Community is fine) with the Kotlin plugin
- **pgAdmin** or **DBeaver** for database browsing
- **Postman** or **HTTPie** for API testing

---

## 1. Clone the Repository

```bash
git clone https://github.com/your-org/taut.git
cd taut
```

---

## 2. Run with Docker (Fastest Path)

This starts PostgreSQL, Redis, pgAdmin, and the backend — no local JDK needed.

```bash
docker compose -f docker/docker-compose.yml up -d
```

Verify everything is healthy:

```bash
docker compose -f docker/docker-compose.yml ps
curl http://localhost:8080/health
# → {"status":"ok","service":"taut-backend"}
```

### Service Endpoints

| Service | URL | Credentials |
|---------|-----|-------------|
| Backend API | http://localhost:8080 | — |
| pgAdmin | http://localhost:5050 | admin@taut.dev / admin |
| PostgreSQL | localhost:5432 | taut / taut |
| Redis | localhost:6379 | — |

### Stop the Stack

```bash
docker compose -f docker/docker-compose.yml down
```

To also wipe the database volume:

```bash
docker compose -f docker/docker-compose.yml down -v
```

---

## 3. Run Backend Locally (Without Docker)

### a) Start PostgreSQL & Redis

Either:
- Install PostgreSQL 16+ and Redis locally, **or**
- Start just the infrastructure containers:

```bash
docker compose -f docker/docker-compose.yml up -d postgres redis
```

### b) Create the Database

```bash
psql -h localhost -U postgres -c "CREATE DATABASE taut;"
psql -h localhost -U postgres -c "CREATE USER taut WITH PASSWORD 'taut';"
psql -h localhost -U postgres -c "GRANT ALL PRIVILEGES ON DATABASE taut TO taut;"
psql -h localhost -U postgres -d taut -c "GRANT ALL ON SCHEMA public TO taut;"
```

> Skip this if you used Docker — `init.sql` handles it automatically.

### c) Build & Run

```bash
cd backend

# Build
./gradlew build

# Run (Flyway migrations execute on startup)
./gradlew run
```

The server starts at **http://localhost:8080**.

### d) Verify

```bash
curl http://localhost:8080/health
# → {"status":"ok","service":"taut-backend"}
```

---

## 4. Accessing PostgreSQL

### Via psql

```bash
psql -h localhost -p 5432 -U taut -d taut
# Password: taut
```

### Via pgAdmin (Docker)

1. Open http://localhost:5050
2. Log in with `admin@taut.dev` / `admin`
3. Add a new server:
   - **Host:** `postgres` (or `localhost` if pgAdmin runs locally)
   - **Port:** `5432`
   - **Username:** `taut`
   - **Password:** `taut`

### Via DBeaver

1. New Connection → PostgreSQL
2. Server: `localhost`, Port: `5432`, Database: `taut`
3. User: `taut`, Password: `taut`

---

## 5. Adding a New API Endpoint

### Step 1 — Define the route

Create or extend a file in `backend/src/main/kotlin/com/taut/routes/`:

```kotlin
// Example: routes/HealthRoutes.kt
package com.taut.routes

import io.ktor.server.application.*
import io.ktor.server.response.*
import io.ktor.server.routing.*

fun Route.healthRoutes() {
    get("/health") {
        call.respond(mapOf("status" to "ok", "service" to "taut-backend"))
    }
}
```

### Step 2 — Register the route

In `backend/src/main/kotlin/com/taut/plugins/Routing.kt`:

```kotlin
fun Application.configureRouting() {
    routing {
        healthRoutes()       // ← add this
        // ... existing routes
    }
}
```

### Step 3 — (Optional) Add a database migration

If the endpoint touches new tables:

1. Create `backend/src/main/resources/db/migration/V1_0_4__add_feature_table.sql`
2. Flyway applies it automatically on the next startup.

### Step 4 — Add a service (for business logic)

```kotlin
// service/FeatureService.kt
class FeatureService(private val ds: DataSource) {
    suspend fun getSomething(): String = withContext(Dispatchers.IO) {
        // JDBC query here
        "result"
    }
}
```

### Step 5 — Test

```bash
# Start the server
./gradlew run

# Test the new endpoint
curl http://localhost:8080/your-new-endpoint
```

---

## 6. Configuration

All defaults live in `backend/src/main/resources/application.conf` and can be
overridden by environment variables:

| Variable | Default | Description |
|----------|---------|-------------|
| `TAUT_HOST` | `0.0.0.0` | Listen address |
| `TAUT_PORT` | `8080` | Listen port |
| `TAUT_DB_URL` | `jdbc:postgresql://localhost:5432/taut` | JDBC URL |
| `TAUT_DB_USER` | `taut` | Database user |
| `TAUT_DB_PASSWORD` | `taut` | Database password |
| `TAUT_JWT_SECRET` | `change-me-in-production` | JWT signing key |

---

## 7. Common Make Commands

```bash
make help            # List all available commands
make run             # Run backend locally
make build           # Build fat JAR (skip tests)
make test            # Run tests
make docker-up       # Start full stack in Docker
make docker-down     # Stop full stack
make db-reset        # Wipe and recreate database
make lint            # Compile-check Kotlin
```

---

## 8. Troubleshooting

| Problem | Solution |
|---------|----------|
| `Connection refused` on DB | Ensure PostgreSQL container is running: `docker compose ps` |
| Port 5432 already in use | Stop local PostgreSQL or change the port mapping in `docker-compose.yml` |
| `./gradlew: Permission denied` | Run `chmod +x backend/gradlew` |
| Flyway migration conflicts | Run `make db-reset` to start fresh |
| Fat JAR not found in Docker | Run `docker compose build --no-cache backend` |

---

## Further Reading

- [API Specification](api-spec.md) — Full REST endpoint documentation
- [Architecture Overview](architecture-overview.md) — System design
- [Database Schema](database-schema.md) — Table definitions
- [Infrastructure Guide](infrastructure.md) — Deployment & cloud setup
