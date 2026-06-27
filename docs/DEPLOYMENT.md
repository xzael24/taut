# TAUT Deployment Guide

> Version: 1.0.0  
> Last updated: 2026-06-25  
> Applies to: TAUT Backend (Kotlin/Ktor) + Android Client

This guide covers deployment of the TAUT backend server in local development, staging, and production environments.

---

## Architecture Overview

```
┌─────────────────┐     ┌──────────────────┐     ┌────────────────┐
│  Android Client │────>│  Load Balancer   │────>│  Ktor Server   │
│  (N+1 devices)  │     │  Envoy / Kong    │     │  Port 9000     │
└─────────────────┘     └──────────────────┘     └───────┬────────┘
                                                         │
                                            ┌────────────┼────────────┐
                                            │            │            │
                                       ┌────▼───┐  ┌────▼───┐  ┌───▼────┐
                                       │Postgres│  │ Redis  │  │ gRPC   │
                                       │ :5432  │  │ :6379  │  │ :9000  │
                                       └────────┘  └────────┘  └────────┘
```

---

## Prerequisites

### Local Development

| Tool | Version | Purpose |
|---|---|---|
| JDK | 21+ (Temurin preferred) | Kotlin compilation and runtime |
| Gradle | 8.11.1+ | Build system (bundled wrapper) |
| Docker | 24.0+ | Container runtime |
| Docker Compose | 2.20+ | Multi-container orchestration |
| Git | 2.40+ | Version control |

### Production

| Tool | Version | Purpose |
|---|---|---|
| Docker | 24.0+ | Container runtime |
| Docker Compose / Kubernetes | 2.20+ / 1.28+ | Orchestration |
| PostgreSQL | 16 | Primary database |
| Redis | 7.x | Caching and queue |

---

## Environment Configuration

### Environment Variables

| Variable | Required | Default | Description |
|---|---|---|---|
| `TAUT_JWT_SECRET` | **Yes** | — | JWT signing and verification secret (min 32 chars) |
| `TAUT_DB_PASSWORD` | **Yes** | — | PostgreSQL database password |
| `TAUT_DB_URL` | No | `jdbc:postgresql://postgres:5432/taut` | Database JDBC connection URL |
| `TAUT_DB_USER` | No | `taut` | Database username |
| `TAUT_HOST` | No | `0.0.0.0` | Server bind address |
| `TAUT_PORT` | No | `9000` | Server port (Ktor HTTP + gRPC) |

### Local .env Setup

```bash
# From project root
cp .env.template .env

# Edit with secure values
vim .env

# Example .env content:
TAUT_JWT_SECRET=your-256-bit-secret-minimum
TAUT_DB_URL=jdbc:postgresql://localhost:5432/taut
TAUT_DB_USER=taut
TAUT_DB_PASSWORD=your-strong-password-here
```

---

## Deployment Methods

### 1. Local Development (Direct JVM)

#### Prerequisites Check
```bash
java -version          # Expect openjdk 21+
docker --version       # Expect 24.0+
docker compose version  # Expect 2.20+
```

#### Start Dependencies
```bash
# Start PostgreSQL and Redis
docker compose -f docker/docker-compose.yml up -d postgres redis

# Check health
docker compose -f docker/docker-compose.yml ps
```

#### Build and Run
```bash
cd backend/

# Compile
./gradlew compileKotlin

# Run tests
./gradlew test

# Start development server
./gradlew run
```

The server will be available at `http://localhost:9000`. Verify with:
```bash
curl http://localhost:9000/health
```

#### Build Fat JAR (for standalone deployment)
```bash
cd backend/
./gradlew clean build -x test
# JAR location: backend/build/libs/taut-backend-*-all.jar

# Run directly
java -jar build/libs/taut-backend-*-all.jar
```

---

### 2. Docker Compose (Full Stack)

#### Staging Deployment

The `docker/docker-compose.yml` file defines a complete staging stack with PostgreSQL, Redis, and the TAUT backend.

```bash
cd docker/

# Deploy full stack
docker compose up -d

# View logs
docker compose logs -f backend

# Check health
docker compose ps
curl http://localhost:9000/health
```

**Services started:**

| Service | Container Name | Port | Purpose |
|---|---|---|---|
| PostgreSQL | `taut-postgres-staging` | 5432 | Primary database |
| Redis | `taut-redis-staging` | 6379 | Cache/queue |
| TAUT Backend | `taut-backend-staging` | 9000 | Ktor server |

#### Development with Hot Reload

The `docker/docker-compose.dev.yml` overlay enables hot-reload development with source code mounting and JPDA debugging.

```bash
cd docker/

# Start with hot reload
docker compose -f docker-compose.yml -f docker-compose.dev.yml up -d

# Debug port available at :5005
# Source changes trigger automatic Gradle recompile
```

#### Docker Compose Configuration Details

**PostgreSQL Service** (`docker-compose.yml`):
```yaml
postgres:
  image: postgres:16-alpine
  environment:
    POSTGRES_DB: taut
    POSTGRES_USER: taut
    POSTGRES_PASSWORD: taut
  volumes:
    - pgdata:/var/lib/postgresql/data
  healthcheck:
    test: ["CMD-SHELL", "pg_isready -U taut -d taut"]
    interval: 10s
    timeout: 5s
    retries: 5
    start_period: 10s
```

**Backend Service** (`docker-compose.yml`):
```yaml
backend:
  build:
    context: ../backend
    dockerfile: Dockerfile
  environment:
    TAUT_HOST: "0.0.0.0"
    TAUT_PORT: "9000"
    TAUT_DB_URL: "jdbc:postgresql://postgres:5432/taut"
    TAUT_DB_USER: taut
    TAUT_DB_PASSWORD: taut
    TAUT_JWT_SECRET: "${TAUT_JWT_SECRET:-change-me-in-production}"
  ports:
    - "9000:9000"
  depends_on:
    postgres:
      condition: service_healthy
    redis:
      condition: service_healthy
  healthcheck:
    test: ["CMD-SHELL", "curl -f http://localhost:9000/health || exit 1"]
    interval: 30s
    timeout: 10s
    retries: 3
    start_period: 30s
```

---

### 3. Production Deployment

#### Docker Image Build

```bash
# Navigate to backend directory
cd backend/

# Build production Docker image
docker build -f Dockerfile -t taut-backend:latest .

# Tag for registry
docker tag taut-backend:latest registry.example.com/taut-backend:v1.0.0

# Push to registry
docker push registry.example.com/taut-backend:v1.0.0
```

#### Production Docker Compose (Example)

Create a production `docker-compose.prod.yml`:

```yaml
version: '3.8'

services:
  postgres:
    image: postgres:16-alpine
    environment:
      POSTGRES_DB: taut
      POSTGRES_USER: taut
      POSTGRES_PASSWORD: ${TAUT_DB_PASSWORD}
    volumes:
      - pgdata:/var/lib/postgresql/data
    networks:
      - taut-network
    deploy:
      resources:
        limits:
          memory: 2G
          cpus: '2'

  redis:
    image: redis:7-alpine
    volumes:
      - redis-data:/data
    networks:
      - taut-network
    deploy:
      resources:
        limits:
          memory: 1G

  backend:
    image: registry.example.com/taut-backend:v1.0.0
    environment:
      TAUT_HOST: "0.0.0.0"
      TAUT_PORT: "9000"
      TAUT_DB_URL: "jdbc:postgresql://postgres:5432/taut"
      TAUT_DB_USER: taut
      TAUT_DB_PASSWORD: ${TAUT_DB_PASSWORD}
      TAUT_JWT_SECRET: ${TAUT_JWT_SECRET}
    ports:
      - "9000:9000"
    networks:
      - taut-network
    depends_on:
      postgres:
        condition: service_healthy
    deploy:
      replicas: 2
      resources:
        limits:
          memory: 512M
          cpus: '1'
    healthcheck:
      test: ["CMD-SHELL", "curl -f http://localhost:9000/health || exit 1"]
      interval: 30s
      timeout: 10s
      retries: 3
      start_period: 30s

volumes:
  pgdata:
  redis-data:

networks:
  taut-network:
    driver: bridge
```

```bash
# Deploy production stack
docker compose -f docker-compose.prod.yml up -d
```

---

## CI/CD Pipeline

### GitHub Actions Workflow

The CI/CD pipeline is defined in `.github/workflows/ci.yml`.

#### Workflow Triggers

```yaml
on:
  push:
    branches: [main]
    paths:
      - 'backend/**'
      - 'android/**'
      - '.github/workflows/ci.yml'
      - 'docker/**'
  pull_request:
    branches: [main]
```

#### Pipeline Stages

```
┌─────────────────────────────────────────────┐
│         CI Pipeline (Parallel)               │
├──────────────────┬──────────────────┬────────┤
│  backend-test    │  android-test    │ docker │
│                  │                  │ -build │
│  ✓ compileKotlin │  ✓ compileDebug │        │
│  ✓ test (56 f.)  │  ✓ testDebug    │ ✓ dock │
│  ✓ upload reports│  ✓ upload rep.  │ ✓ smok │
└──────────────────┴──────────────────┴────────┘
                    │
                    ▼
            ┌───────────────┐
            │  docker-build  │
            │  (after both   │
            │   pass)        │
            │                │
            │  ✓ Docker build │
            │  ✓ Smoke test  │
            └───────────────┘
```

#### Stage 1: Backend Test

```yaml
backend-test:
  runs-on: ubuntu-latest
  services:
    postgres:
      image: postgres:16-alpine
      env:
        POSTGRES_DB: taut
        POSTGRES_USER: taut
        POSTGRES_PASSWORD: taut
  steps:
    - uses: actions/checkout@v4
    - uses: actions/setup-java@v4
      with:
        java-version: '21'
        distribution: 'temurin'
    - run: ./gradlew compileKotlin compileTestKotlin
    - run: ./gradlew test
      env:
        TAUT_DB_URL: jdbc:postgresql://localhost:5432/taut
```

**Test status**: 56 test files, 30/30 unit tests pass, 26/26 routes need fix.

#### Stage 2: Android Test

```yaml
android-test:
  runs-on: ubuntu-latest
  steps:
    - uses: actions/checkout@v4
    - uses: actions/setup-java@v4
      with:
        java-version: '21'
        distribution: 'temurin'
    - run: ./gradlew compileDebugKotlin
    - run: ./gradlew testDebugUnitTest
```

**Test status**: 6 test files compiling.

#### Stage 3: Docker Build (after backend + android pass)

```yaml
docker-build:
  needs: [backend-test, android-test]
  steps:
    - run: docker build -f Dockerfile -t taut-backend:${{ github.sha }} .
    - run: docker run -d --name taut-smoke-test taut-backend:${{ github.sha }}
    - run: sleep 10 && docker logs taut-smoke-test
```

---

## Docker Configuration

### Production Dockerfile

Location: `backend/Dockerfile`

#### Multi-stage Build
```
Stage 1: builder (openjdk:21-jdk-slim)
  - Gradle 8.11.1
  - Dependency caching (Gradle wrapper + build files)
  - Source compilation
  - Fat JAR assembly

Stage 2: runtime (openjdk:21-jre-slim)
  - Non-root user (taut, UID 1001)
  - curl for health checks
  - EXEC form CMD
```

```dockerfile
FROM openjdk:21-jdk-slim AS builder
WORKDIR /app
# ... dependency caching and build ...
RUN ./gradlew --no-daemon clean build -x test

FROM openjdk:21-jre-slim
WORKDIR /app
COPY --from=builder /app/build/libs/*-all.jar /app/backend-all.jar
USER taut
EXPOSE 9000
HEALTHCHECK --interval=30s --timeout=10s --start-period=40s --retries=3 \
    CMD curl -f http://localhost:9000/health || exit 1
CMD ["java", "-jar", "/app/backend-all.jar"]
```

#### Dev Dockerfile

Location: `backend/Dockerfile.dev`

Uses `gradle:8.7-jdk17-alpine` image with source code mounted as a volume for hot-reload via `gradle --continuous`.

---

## Health Checks

### Docker Health Check

The backend container has a built-in health check that probes the `/health` endpoint:

| Parameter | Value |
|---|---|
| Test | `curl -f http://localhost:9000/health \|\| exit 1` |
| Interval | 30s |
| Timeout | 10s |
| Retries | 3 |
| Start Period | 30-40s |

### Health Check Response

**Healthy (200)**:
```json
{
  "status": "healthy",
  "version": "1.0.0",
  "uptime_seconds": 3600,
  "database": "connected",
  "redis": "connected"
}
```

**Unhealthy (503)**:
```json
{
  "status": "unhealthy",
  "version": "1.0.0",
  "uptime_seconds": 120,
  "database": "disconnected",
  "database_error": "Connection refused"
}
```

### Manual Verification
```bash
# Direct health check
curl -f http://localhost:9000/health

# Docker health status
docker inspect --format='{{.State.Health.Status}}' taut-backend-staging

# Logs
docker logs taut-backend-staging
```

---

## Database Management

### PostgreSQL Connection

```bash
# Connect to local database
docker exec -it taut-postgres-staging psql -U taut -d taut

# Common commands
\dt     -- List all tables
\d+     -- Describe table schema
\!      -- Shell escape
```

### Backup and Restore

```bash
# Backup
docker exec -t taut-postgres-staging pg_dump -U taut taut > backup_$(date +%Y%m%d).sql

# Restore
cat backup.sql | docker exec -i taut-postgres-staging psql -U taut taut
```

### Database Migrations

Database schema is managed programmatically through DDL in `Database.kt`. Migrations are applied on application startup:

1. Tables are created if not existing (`CREATE TABLE IF NOT EXISTS`)
2. Schema version is tracked in a `schema_version` table
3. Backward-compatible migration scripts are applied incrementally

---

## Monitoring & Observability

### Health Endpoint
The `/health` endpoint provides basic service health information including database and Redis connection status.

### Logging
- **Format**: Structured JSON via Logback
- **Levels**: TRACE, DEBUG, INFO, WARN, ERROR
- **Output**: stdout (Docker logs)
- **Shipped to**: Loki for aggregation (production)

### Metrics (Planned)
- Ktor metrics plugin for request rate, latency, error rate
- Prometheus metrics endpoint (`/metrics`)
- Grafana dashboards

---

## Troubleshooting

### Common Issues

#### Database Connection Failed

**Symptoms**: Server fails to start, `database: disconnected` in health check

**Solutions**:
```bash
# Verify PostgreSQL is running
docker ps | grep postgres

# Check PostgreSQL logs
docker logs taut-postgres-staging

# Test connection from backend container
docker exec taut-backend-staging nc -zv postgres 5432
```

#### JWT Secret Not Set

**Symptoms**: Authentication failures, 401 errors

**Solutions**:
```bash
# Ensure TAUT_JWT_SECRET is set in environment
echo $TAUT_JWT_SECRET

# For Docker, set in environment section of docker-compose.yml
environment:
  TAUT_JWT_SECRET: "${TAUT_JWT_SECRET}"
```

#### Container Restart Loop

**Solutions**:
```bash
# Check logs
docker logs taut-backend-staging

# Verify all dependency services are healthy
docker compose ps

# Increase start_period in healthcheck config
```

#### Port Conflicts

**Symptoms**: Port already in use errors

**Solutions**:
```bash
# Check what's using port 9000
netstat -ano | grep 9000

# Change port via environment variable
export TAUT_PORT=9001
```

---

## Security Considerations

### Production Checklist

- [ ] `TAUT_JWT_SECRET` set to a cryptographically random 256-bit value
- [ ] `TAUT_DB_PASSWORD` set to a strong, unique password
- [ ] TLS/SSL configured for gRPC and HTTP connections
- [ ] API Gateway (Envoy/Kong) configured with rate limiting
- [ ] PostgreSQL exposed only to internal network (not publicly)
- [ ] Container running as non-root user (`taut`, UID 1001)
- [ ] Regular database backups configured
- [ ] Monitoring and alerting set up
- [ ] Log retention policy configured
- [ ] UU PDP compliance requirements addressed

### Secret Management

For production, use a secret management solution:

```bash
# Environment variables (basic)
export TAUT_JWT_SECRET=$(openssl rand -base64 32)

# Docker secrets (recommended)
echo "your-secret" | docker secret create taut_jwt_secret -
```

---

## Known Issues

### Duplicate Workflows Directory
There is a stale duplicate workflow at `.github/workflows/workflows/ci.yml`. The canonical CI file is `.github/workflows/ci.yml`. To clean up:

```bash
rm -rf .github/workflows/workflows/
```

### Route Tests
Note that 26/26 route tests currently need fixes. See `backend/src/test/kotlin/com/taut/routes/` for test files:

| Test File | Status |
|---|---|
| `SecurityTest.kt` | Needs review |
| `TransactionRoutesTest.kt` | Needs review |
| `CatalogRoutesTest.kt` | Needs review |
