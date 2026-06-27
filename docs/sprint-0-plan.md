# SPRINT 0 — Foundation & Architecture Spike

**Durasi:** 2 minggu (9 hari kerja)
**CEO Directive:** *"No business logic until the foundation is proven."*

## Objectives

1. **DevOps Ready** — Docker, PostgreSQL, CI/CD pipeline siap
2. **Phase 0 Spikes** — 3 asumsi paling berisiko divalidasi:
   a. gRPC bidirectional sync (backend ↔ Android peer-to-peer)
   b. SQLCipher encryption at rest (Android)
   c. Conflict resolution via Lamport clocks (no data loss)
3. **Development Environment** — Build dari command line, linting, test runner
4. **Project Management** — Sprint backlog, Definition of Done, acceptance criteria

## Assigned Tasks

### 🏗️ DevOps (Task 1)
- Dockerfile untuk backend (Ktor + Java 17)
- docker-compose: backend + PostgreSQL 16 + Redis (optional)
- .github/workflows/ci.yml: Gradle build → test → lint
- Postgres init scripts (migration auto-run)
- Local development script (start-dev.sh / start-dev.bat)

### 📡 Phase 0 — Sync Protocol (Task 2)
- gRPC service definition (proto file) untuk TransactionSync
- Backend gRPC server stub (jalan)
- Android gRPC client stub (compile)
- Test: 2 offline device → simpan transaksi → sync → no data loss
- Doc: Hasil spike — apa yang berhasil, apa yang tidak

### 🔐 Phase 0 — Encryption & Local (Task 3)
- SQLCipher integration test (Android: encrypt → write → read → decrypt)
- Android Keystore key management (AES-256-GCM)
- Test: app dihentikan paksa → data aman
- Shared-device multi-operator isolation test

### 📋 PM Coordination (Parallel)
- Breakdown Sprint 1 stories from sprint-1-plan.md into granular tasks
- Setup Definition of Done checklist

## Definition of Done for Sprint 0
- [ ] Docker compose up → backend running + Postgres migrated
- [ ] GitHub Actions CI passing (build + lint + test)
- [ ] gRPC sync: 2 devices → offline transactions → sync → consistent
- [ ] SQLCipher: device wiped → data unreadable without passphrase
- [ ] Android project compiles with `./gradlew assembleDebug`
- [ ] Backend project compiles with `./gradlew build`
- [ ] Conflict resolution: concurrent edits → deterministic merge
