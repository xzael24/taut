# Sprint 0 — Foundation & Architecture Spike

**Durasi:** 2 minggu (24 Juni - 7 Juli 2026)
**Goal:** Semua fondasi teknis siap sebelum Sprint 1 ngoding beneran.

---

## Workstreams

### WS-1: DevOps & Infrastructure
| Task | Assignee | Est. | Description |
|------|----------|------|-------------|
| Docker setup | DevOps | 2d | Dockerfile backend + docker-compose (PostgreSQL, Redis, App) |
| GitHub Actions CI | DevOps | 1d | Build backend, lint, test otomatis tiap push |
| PostgreSQL init | DevOps | 1d | Migration file jalan otomatis di docker-compose up |
| Dev environment docs | DevOps | 0.5d | Panduan setup dari 0 buat dev baru |
| UAT/staging server | DevOps | 2d | VM/server sederhana buat testing tim |

### WS-2: Phase 0 Spike — Sync Protocol ⚡
| Task | Assignee | Est. | Description |
|------|----------|------|-------------|
| gRPC proto definition | Tech Lead | 1d | Definisikan service SyncService di protobuf |
| gRPC server dummy | BE/Architect | 2d | Server kecil yg nerima sync request |
| gRPC client dummy | Android | 2d | Client Android yg kirim sync request |
| Bidi streaming test | Tech Lead | 1d | 2 device offline → sync → verifikasi data |
| Conflict resolution | Tech Lead | 2d | Simulasi: device A & B edit data beda → resolve |
| Spike report | Tech Lead | 0.5d | Dokumen hasil: apa yg berhasil, apa yg gak |

### WS-3: Phase 0 Spike — SQLCipher & Security
| Task | Assignee | Est. | Description |
|------|----------|------|-------------|
| SQLCipher setup Android | Android | 1d | Room + SQLCipher berhasil jalan |
| AES key via Keystore | Android | 1d | CryptoManager functional test |
| PII field encryption test | Android | 0.5d | Test encrypt/decrypt data sensitif |
| UU PDP checklist | Tech Lead | 0.5d | Review final compliance |

### WS-4: Project Management
| Task | Assignee | Est. | Description |
|------|----------|------|-------------|
| Kanban board created | PM/CEO | 0.5d | Task per sprint visible |
| Sprint 1 backlog | PM | 1d | User stories breakdown ready |
| Standup schedule | PM | 0.5d | Daily sync via WhatsApp/Discord |
| Definition of Done | ALL | 1d | Disepakati bareng |

---

## Definition of Done (Sprint 0)

- [ ] Docker-compose up → backend jalan + PostgreSQL siap
- [ ] CI pipeline green di GitHub (build backend)
- [ ] gRPC sync bisa kirim data dari Android → backend
- [ ] Conflict resolution tested: 2 device offline → sync → 0 data loss
- [ ] SQLCipher encrypt → decrypt → verifikasi byte sama
- [ ] Sprint 1 backlog ready buat diambil engineer
- [ ] Semua P0 spike findings didokumentasi

---

## Gates to Sprint 1

Sprint 1 **TIDAK DIMULAI** sampe:
1. gRPC sync POC **BERHASIL** (data nyampe, conflict resolve)
2. SQLCipher **TERVERIFIKASI** (encrypt/decrypt matching)
3. CI pipeline **GREEN**
