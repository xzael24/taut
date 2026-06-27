# TAUT — Infrastructure Design
## Cloud Architecture & Operations
**Version:** 2.0
**Date:** 23 Juni 2026

---

## Table of Contents

1. [Cloud Provider Recommendations](#1-cloud-provider-recommendations)
2. [Environment Topology](#2-environment-topology)
3. [Compute Architecture](#3-compute-architecture)
4. [Database Architecture](#4-database-architecture)
5. [Networking](#5-networking)
6. [Scaling Strategy](#6-scaling-strategy)
7. [Monitoring & Observability](#7-monitoring--observability)
8. [CI/CD Pipeline](#8-cicd-pipeline)
9. [Cost Estimates](#9-cost-estimates)
10. [Disaster Recovery](#10-disaster-recovery)
11. [Operational Runbooks](#11-operational-runbooks)

---

## 1. Cloud Provider Recommendations

### 1.1 Primary Recommendation: Google Cloud Platform (GCP)

**Why GCP over AWS or Azure:**

| Factor | GCP | AWS | Azure |
|--------|-----|-----|-------|
| **Jakarta region (asia-southeast2)** | ✅ **Launched 2020** | ❌ No Jakarta region (closest: Singapore) | ❌ No Jakarta region |
| **Data residency (UU PDP Pasal 46)** | ✅ Compliant | ❌ Data must stay in Indonesia | ❌ |
| **TimescaleDB support** | ✅ Native via Cloud SQL + pg_partman | ✅ RDS PostgreSQL | ✅ Flexible Server |
| **Kubernetes (GKE vs EKS vs AKS)** | ✅ Best managed k8s, auto-scaling, regional HA | ✅ Good, but more ops overhead | ✅ Good, but Windows-centric |
| **Cost for 4-16 nodes** | ✅ $800-2,500/mo (committed use discounts) | ✅ Similar | ❌ Often 20-30% more |
| **Cloud SQL managed PostgreSQL** | ✅ Min $400/mo (500GB SSD) | ✅ $500+ | ✅ $450+ |
| **Memorystore Redis** | ✅ $150-400/mo (10GB) | ✅ ElastiCache similar | ✅ Azure Cache similar |
| **Kominfo compliance history** | ✅ Multiple government deployments | ✅ Some | ❌ Limited |

**Decision: GCP asia-southeast2 (Jakarta) for all production data.**

---

## 2. Environment Topology

### 2.1 Environment Breakdown

```
                        Production (prod)
   GCP Project: taut-prod-1234
   Region: asia-southeast2 (Jakarta)
   DR: asia-southeast1 (Singapore) — Kominfo-approved cross-border
   DNS: taut.id, *.taut.id
   GKE Cluster: taut-prod-cluster (4-16 nodes, n2-standard-4)
   Cloud SQL: taut-prod-db (PostgreSQL 15 + TimescaleDB)
   Memorystore: taut-prod-redis (10GB, standard HA)
   SMS Budget: ~45,000 SMS/month (MVP)

                        Staging (staging)
   GCP Project: taut-staging-5678
   Region: asia-southeast2 (Jakarta)
   DNS: staging.taut.id
   GKE Cluster: taut-staging-cluster (2-4 nodes, n2-standard-4)
   Cloud SQL: taut-staging-db (PostgreSQL 15, 100GB SSD)
   Memorystore: taut-staging-redis (2GB, HA disabled)
   Purpose: Pre-production validation. Mirrors prod data (anonymized)

                        Development (dev)
   GCP Project: taut-dev-9012
   Region: asia-southeast2 (Jakarta)
   DNS: dev.taut.id
   GKE Cluster: taut-dev-cluster (1-2 nodes, e2-standard-2)
   Cloud SQL: taut-dev-db (PostgreSQL 15, 50GB SSD)
   Memorystore: taut-dev-redis (1GB, single node)
   Purpose: Daily development, automated testing, feature branches
```

### 2.2 GCP Project Structure

```
Organization: taut.com (GCP Organization)
├── Folder: Production
│   └── Project: taut-prod-1234
│       ├── Services: GKE, Cloud SQL, Memorystore, GCS, Cloud NAT
│       ├── IAM:
│       │   ├── roles/taut.admin (full access)
│       │   ├── roles/taut.devops (deploy + read logs)
│       │   ├── roles/taut.readonly (monitoring)
│       │   └── roles/taut.dbadmin (database admin)
│       └── VPC: taut-prod-vpc
├── Folder: Non-Production
│   ├── Project: taut-staging-5678
│   └── Project: taut-dev-9012
└── Folder: Security
    └── Project: taut-security-audit
        └── Services: Cloud Audit Logs, Forseti, Chronicle
```

### 2.3 IAM Policy

| Role | Permissions | Assigned To |
|------|-------------|-------------|
| `roles/taut.admin` | Full project access, IAM admin, security | CTO, Lead Architect |
| `roles/taut.devops` | Deploy to GKE, read logs, restart services | DevOps engineers (3 people) |
| `roles/taut.readonly` | Read monitoring, logs, billing | All engineers, PM |
| `roles/taut.dbadmin` | Full PostgreSQL access, migration | Lead BE + DevOps |
| `roles/taut.support` | Read transactions, user data (masked) | Customer support (Fase 2+) |

---

## 3. Compute Architecture

### 3.1 GKE Cluster Configuration

**Production GKE Cluster:**
- **Name:** taut-prod-cluster
- **Location:** asia-southeast2 (multi-zone: a, b, c)
- **Node Pools:**
  - **default-pool:** n2-standard-4 (4 vCPU, 16GB RAM), 4-16 nodes, auto-scaling, 100GB pd-standard
  - **db-pool:** n2-standard-2 (2 vCPU, 8GB RAM), 2-4 nodes, for PgBouncer sidecars
  - **batch-pool:** n2-standard-2, 0-6 nodes, spot/preemptible, for SMS workers + report generation
- **Autoscaling:** min 4, max 16 nodes (cluster), +20 max with burst
- **Workload Identity:** enabled (taut-prod-1234.svc.id.goog)
- **Private cluster:** nodes have private IPs only; control plane accessible from authorized networks
- **Networking:** VPC-native (alias IP), secondary ranges for pods (10.1.0.0/16) and services (10.2.0.0/20)

### 3.2 Service Deployment Layout

| Service | Min Replicas | Max Replicas | CPU req/lim | Memory req/lim | Port | HPA Metric |
|---------|-------------|-------------|-------------|----------------|------|------------|
| sync-engine | 4 | 10 | 500m / 1 | 512Mi / 1Gi | 9090 (gRPC) | 1000 req/s per pod |
| auth-service | 2 | 6 | 250m / 500m | 256Mi / 512Mi | 9091 (gRPC) | CPU > 70% |
| tx-processor | 3 | 8 | 500m / 1 | 512Mi / 1Gi | 9092 (gRPC) | Redis stream > 1000 |
| sms-service | 2 | 4 | 200m / 500m | 256Mi / 512Mi | 9093 (gRPC) | Queue depth > 100 |
| dashboard-api | 2 | 6 | 250m / 1 | 512Mi / 1Gi | 8080 (REST) | Req rate > 500/min |
| catalog-service | 2 | 4 | 200m / 500m | 256Mi / 512Mi | 9094 (gRPC) | Req rate > 1000/s |

### 3.3 PgBouncer Sidecar

Each database-facing pod runs a PgBouncer sidecar container:
- **Image:** bitnami/pgbouncer:1.22
- **Port:** 6432
- **Pool mode:** transaction
- **Default pool size:** 25
- **Max client connections:** 200
- **Max DB connections:** 50
- **Idle timeout:** 300s
- **Query timeout:** 30s

---

## 4. Database Architecture

### 4.1 Cloud SQL for PostgreSQL

- **Engine:** PostgreSQL 15
- **Tier:** db-custom-4-15360 (4 vCPU, 15GB RAM)
- **Storage:** 500GB SSD, auto-increase enabled
- **HA:** Regional (multi-zone), point-in-time recovery enabled
- **Backup:** Daily at 02:00, 30-day retention, 7-day WAL retention
- **Maintenance window:** Sunday 04:00
- **Key flags:** shared_buffers=25% RAM, work_mem=64MB, wal_buffers=16MB
- **Read replica:** db-custom-2-7680 in asia-southeast2-b (for analytics/dashboards)
- **DR replica:** db-custom-4-15360 in asia-southeast1 (Singapore, cross-region)

### 4.2 TimescaleDB Configuration

```
Extensions: timescaledb
Hypertables:
  - transactions: chunk_interval = 1 day, compress after 30 days
  - price_references: chunk_interval = 30 days, compress after 90 days
Continuous Aggregates:
  - cagg_daily_totals (per bank_sampah_id per day)
  - Refresh: every 1 hour, offset: 1h–3d
  - TimescaleDB continuous aggregate replaces manual materialized view refresh
Retention:
  - Raw transactions: kept 1 year (compressed)
  - Raw price_references: kept indefinitely (compressed)
```

### 4.3 Redis (Memorystore) Data Layout

| Key Pattern | Type | TTL | Purpose |
|-------------|------|-----|---------|
| `processed_ids:*` | String (Bloom) | 90 days | Dedup for idempotency |
| `stream:tx:ingestion` | Stream | 7 days | Transaction ingestion queue |
| `stream:sms:pending` | Stream | 7 days | SMS dispatch queue |
| `cache:price_catalog` | Hash | 5 min | Current price catalog cache |
| `cache:user:*` | String (JSON) | 15 min | User profile cache |
| `rate_limit:*` | Sorted Set | 1 min | Rate limiter counters |
| `session:*` | String | 30 min | Active session metadata |
| `pubsub:dashboard:*` | Pub/Sub | — | Real-time dashboard events |

---

## 5. Networking

### 5.1 VPC Design

- **Name:** taut-prod-vpc
- **Subnet:** 10.0.0.0/20 (asia-southeast2), private Google access enabled
- **Pods:** 10.1.0.0/16 (secondary range)
- **Services:** 10.2.0.0/20 (secondary range)
- **Cloud NAT:** 2048 ports per VM, 2 reserved static IPs for outbound
- **Firewall rules:**
  - ALLOW: health checks from 35.191.0.0/16, 130.211.0.0/22
  - ALLOW: internal VPC (10.0.0.0/8)
  - DENY: all other ingress
- **VPC peering:** Cloud SQL + Memorystore via private services access

### 5.2 DNS Architecture

- **Provider:** Cloudflare (Pro plan)
- **Zone:** taut.id
- **Records:** api.taut.id, sync.taut.id, dashboard.taut.id → GCP LB IP (proxied)
- **SSL/TLS:** TLS 1.3 via Cloudflare + GCP Managed Certs
- **DDoS:** Cloudflare DDoS protection (always-on)

---

## 6. Scaling Strategy

### 6.1 Horizontal Scaling Decisions

| Component | Trigger | Auto-scaling | Max |
|-----------|---------|--------------|-----|
| sync-engine | gRPC > 800/s/pod | HPA (CPU + custom) | 16 |
| tx-processor | Redis stream > 500 | HPA (custom: stream depth) | 12 |
| auth-service | p95 latency > 200ms | HPA (CPU + memory) | 8 |
| sms-service | Queue > 100 | HPA (custom: queue depth) | 6 |
| dashboard-api | Rate > 500/min/pod | HPA (CPU) | 8 |
| GKE nodes | Pod pending > 30s | Cluster autoscaler | 20 |
| Cloud SQL | Connections > 150 | Manual tier upgrade | db-custom-8-30720 |

### 6.2 Burst Handling

- **MVP (20 banks):** ~1,000 tx/day, ~200 tx/hour peak, ~20 devices syncing
- **Month 3 (200 banks):** ~6,000 tx/day, ~1,200 tx/hour peak, 200 devices syncing every 15 min
- **Capacity:** Sync Engine supports 4,000 req/s (4 pods × 1,000 req/s); Redis handles 100K backlog; PgBouncer pools 200 client connections to 50 DB connections
- **Bottlenecks to watch:** PostgreSQL write throughput, Redis stream memory, SMS provider rate limits

---

## 7. Monitoring & Observability

### 7.1 Monitoring Stack

| Component | Tool | Purpose |
|-----------|------|---------|
| Metrics | Prometheus + Mimir (Grafana Cloud) | Service metrics, resource usage |
| Logs | Loki (Grafana Cloud) | Structured JSON logs from all services |
| Traces | Tempo (Grafana Cloud) | Distributed tracing for gRPC calls |
| Dashboards | Grafana | 5 pre-built dashboards |
| Alerts | Grafana OnCall + PagerDuty | P0 (page), P1 (notify), P2 (track) |
| Uptime | Cloudflare | External health checks every 1 min |
| Synthetic | Checkly | End-to-end API + gRPC tests every 5 min |

### 7.2 Key Metrics

**RED Method (Rate/Errors/Duration):**
- `grpc_server_requests_total{service,method,status}`
- `grpc_server_errors_total{service,error_code}`
- `grpc_server_duration_seconds{service,method}[p50,p95,p99]`
- `http_server_requests_total{service,endpoint,status}`

**Business Metrics:**
- `transactions_created_total{status}`
- `transaction_value_satuan_total`
- `active_banks_total`, `active_operators_total`
- `sms_sent_total{type}` — OTP vs receipt
- `sms_cost_satuan_total` — daily cumulative

**Infrastructure:**
- `k8s_node_cpu_usage`, `container_memory_usage`
- `pgbouncer_pool_usage`, `postgres_connection_count`
- `postgres_replication_lag` (alert if > 300s)
- `redis_memory_usage`, `redis_stream_length`

### 7.3 Dashboards

| Dashboard | Panels | Refresh |
|-----------|--------|---------|
| Service Overview | Request rate, error rate, p95 latency, CPU/mem per service | 30s |
| Business KPIs | Tx/hour, total value/day, active banks, SMS cost MTD | 1min |
| Sync Health | Sync success rate (7d), pending tx count, failed tx, p95 latency | 30s |
| Database | Connection count, replication lag, query latency p95/p99 | 30s |
| Infrastructure | GKE node/pod count, container restarts, Redis memory | 1min |

### 7.4 Alert Rules

| Severity | Condition | Response Time | Channel |
|----------|-----------|--------------|---------|
| **P0** | Error rate > 5% over 5 min | 15 min | PagerDuty + Slack + SMS |
| **P0** | Sync failure rate > 1% over 15 min | 15 min | PagerDuty + Slack |
| **P0** | Database or Redis unreachable | 15 min | PagerDuty + Slack |
| **P1** | p95 latency > 2s over 5 min | 30 min | Slack + email |
| **P1** | Replication lag > 300s | 30 min | Slack |
| **P1** | Disk usage > 80% | 30 min | Slack |
| **P1** | Device offline > 48h (> 5% of devices) | Next working day | Slack |
| **P2** | Daily SMS cost > 2x moving avg | Next working day | Slack |
| **P2** | TLS certificate < 14 days to expiry | Next working day | Slack |

### 7.5 Structured Logging

All services log JSON with: `timestamp`, `level`, `service`, `request_id`, `trace_id`, `message`, `user_id`, `device_id`, `duration_ms`.

**Never log:** PINs, passwords, OTP codes, full phone numbers (mask: 0812****789), JWT tokens, raw SQL queries (log query name instead).

**Log retention:** stdout (Loki) → 7 days; error logs → 30 days; audit logs → 1 year (GCS coldline after 90d).

---

## 8. CI/CD Pipeline

### 8.1 Pipeline Stages

```
Commit → Lint + Check → Unit + Integration Tests → Build Docker Images
    → Push to GCR → Deploy to Staging → Smoke Tests
    → Canary Deploy (10% prod traffic, 15 min observation)
    → Full Production Deploy (manual approval: CTO + DevOps)
```

### 8.2 CI (GitHub Actions)

- **Lint step:** ktlint + detekt (Kotlin), buf lint (Protobuf), sqlfluff (SQL), APK size check (< 15MB)
- **Test step:** Kotlin unit tests, Android instrumentation tests, gRPC integration tests (docker-compose), Flyway migration tests, Trivy security scan (HIGH/CRITICAL only)
- **Build step:** Assemble release APK, build Docker images for all 6 services, push to GCR
- **APK artifact:** Uploaded as build artifact for distribution

### 8.3 CD (GitHub Actions + ArgoCD)

- **Staging:** Auto-deploy on main merge (after CI passes)
- **Canary:** Auto-deploy after staging smoke tests (10% of devices, 15 min observation window). Auto-rollback if error rate > 1%
- **Production:** Manual approval (two-person: DevOps + CTO). Rolling update via GKE
- **Rollback:** `kubectl rollout undo deployment/<name> -n taut` or git revert + re-deploy

### 8.4 Migration Deployment

- Run on read replica first → promote replica → run on old primary (now replica)
- Zero-downtime: additive schema changes only (add column nullable, create index CONCURRENTLY)
- Destructive changes (DROP COLUMN): 4-release process across 2-4 weeks

---

## 9. Cost Estimates

### 9.1 Monthly Cost Breakdown (Phase 1 — MVP)

| Service | Configuration | Cost/Month (USD) |
|---------|--------------|------------------|
| GKE default pool (4 × n2-standard-4, reserved 1yr) | ~$700 |
| GKE db pool (2 × n2-standard-2, on-demand) | ~$200 |
| GKE batch pool (0-4 spot instances) | ~$50 |
| GKE cluster fee | ~$70 |
| Cloud SQL PostgreSQL (4 vCPU, 15GB, 500GB SSD, HA) | ~$600 |
| Read replica (2 vCPU, 7.5GB, 200GB SSD) | ~$200 |
| DR replica (Singapore, 4 vCPU, 15GB, 500GB SSD) | ~$400 |
| Cloud SQL backup storage | ~$100 |
| Memorystore Redis (10GB, Standard HA) | ~$200 |
| Cloudflare Pro (DNS, CDN, WAF, DDoS) | ~$100 |
| GCS (audit logs + backups) | ~$15 |
| SMS Gateway (~45K msg/mo) | ~$450 |
| Cloud NAT + Load Balancer | ~$60 |
| Grafana Cloud (metrics + logs + traces) | ~$120 |
| Secret Manager | ~$10 |
| Synthetic monitoring | ~$20 |
| **Total (Phase 1 MVP)** | **~$3,295** |

### 9.2 Cost Scaling Projections

| Phase | Banks | Monthly Cost | Key Drivers |
|-------|-------|-------------|-------------|
| Phase 0 (M1-3) | 20 | ~$3,400 | Base infra + small SMS |
| Phase 1 (M4-5) | 200 | ~$4,350 | SMS grows to 45K/mo |
| Phase 2 (M6-8) | 500 | ~$7,000 | Scale GKE to 8 nodes, PSP fees |
| Phase 3 (M9-12) | 1,000+ | ~$12,500 | Scale GKE to 12-16 nodes, higher SMS + support |

### 9.3 Cost Optimization

| Strategy | Savings | When |
|----------|---------|------|
| GKE Committed Use (1yr, baseline 4 nodes) | 20-30% | Month 1 |
| Cloud SQL Committed Use (1yr) | 20-30% | Month 1 |
| Spot VMs for batch workloads | 60-80% | Month 1 |
| SMS batching (aggregated end-of-day receipts) | 40-50% | Month 3 |
| Direct carrier SMS routing | 30-50% | Month 6 |
| GCS lifecycle (coldline after 90d) | 30% | Month 1 |

---

## 10. Disaster Recovery

### 10.1 RTO and RPO

| Scenario | RTO | RPO | Mitigation |
|----------|-----|-----|------------|
| Pod crash | <30s | 0 | Kubernetes auto-restart |
| Node failure | <2min | 0 | GKE multi-zone node pool + PDB |
| Zone failure (Jakarta) | <5min | <1min | Regional cluster, Cloud SQL HA |
| Region failure (Jakarta) | <1h | <5min | Cross-region failover to Singapore |
| Database corruption | <4h | <5min | PITR (15-min WAL to GCS) |
| Accidental deletion | <4h | <15min | PITR to point before deletion |
| Security incident | <24h | <1h | Contain + restore from clean backup |
| SMS provider outage | <30min | 0 | Failover to secondary provider |

### 10.2 Failover Plan (Jakarta → Singapore)

1. **Detect:** Cloud LB health checks fail across all Jakarta zones (alert: P0)
2. **Decide:** CTO/DevOps confirms failover (within 5 min)
3. **Execute (automated, <10 min):**
   - Promote DR PostgreSQL replica (Singapore) to primary
   - Update DNS: api.taut.id → Singapore LB IP
   - Deploy GKE cluster in asia-southeast1 from pre-baked images
   - Redirect SMS traffic to Singapore-hosted SMS gateway
4. **Verify:** Run smoke tests, check data consistency, notify operators
5. **Restore (after Jakarta recovers, <2h):** Set up reverse replication, switch back

### 10.3 Backup Strategy

| Component | Frequency | Retention | Location |
|-----------|-----------|-----------|----------|
| PostgreSQL full | Daily | 30 days | GCS asia-southeast2 |
| WAL archives | Continuous (15 min) | 7 days | GCS asia-southeast2 |
| DR replica | Streaming | — | GCP asia-southeast1 |
| Redis snapshot (RDB) | Every 6h | 7 days | GCS |
| Audit logs | Continuous | 1 year | GCS (coldline after 90d) |

**Backup verification:** Daily restore test on staging; weekly full restore on isolated project; quarterly DR failover drill.

---

## 11. Operational Runbooks

### 11.1 Common Operations

| Operation | Command |
|-----------|---------|
| Scale up GKE | `gcloud container clusters resize taut-prod-cluster --node-pool default-pool --num-nodes 8 --region asia-southeast2` |
| Restart service | `kubectl rollout restart deployment/sync-engine -n taut` |
| View logs | `kubectl logs -l app=sync-engine -n taut --tail=100 --since=1h` |
| Run migration | `flyway migrate -url=jdbc:postgresql://...` (emergency only — CI/CD is the automated path) |
| Force device resync | `curl -X POST https://api.taut.id/v1/devices/{id}/resync` |
| Remote wipe device | `curl -X POST https://api.taut.id/v1/devices/{id}/wipe` (requires WhatsApp verification) |

### 11.2 Incident Response Flow

```
1. ACKNOWLEDGE alert (PagerDuty / Slack) — 5 min
2. DECLARE severity (P0/P1/P2) + open incident channel
3. TRIAGE (<15 min): Is DB up? Pods running? DNS resolving?
4. MITIGATE: Rollback deployment / scale up / failover / block IPs
5. COMMUNICATE: Internal (Slack every 30 min) + External (status page if user-facing)
6. RESOLVE + smoke tests + close incident
7. POST-MORTEM within 72h: root cause, action items, runbook updates
```

### 11.3 Weekly Ops Checklist

- **Monday:** Review weekly Grafana business metrics + weekend paging events + SMS provider status
- **Tuesday:** Run backup restore test on staging + review error log trends (Loki) + check TLS certificate expiry
- **Wednesday:** Review GCP cost trends + device offline alerts + Dependabot PRs
- **Thursday:** Performance review (latency, query perf) + capacity planning (disk, connections) + update runbooks
- **Friday:** Deploy non-critical changes + review next week maintenance windows + security scan results

---

*End of Infrastructure Document*
