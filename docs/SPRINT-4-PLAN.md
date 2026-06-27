# Sprint 4 — Final Sprint: Security Hardening + Production Deployment

**Project:** TAUT — Platform Daur Ulang Digital  
**Duration:** 2 minggu (10 hari kerja)  
**Goal:** ✅ READY FOR PRODUCTION — Zero critical vulnerabilities, verified deployment runbook, signed APK on Play Store  

> **Context:** Sprint 2 completed 49 fixes (4 CRITICAL, 15 MAJOR, 12 MEDIUM, 18 LOW).  
> All 80 tests pass (10 backend + 70 Android). CI workflows exist but are untested.  
> Docker Desktop is NOT available on the dev machine — deployment runbook assumes user will test `docker compose up` when Docker becomes available.

---

## Table of Contents

1. [Security Hardening](#1-security-hardening)
2. [Production Deployment](#2-production-deployment)
3. [Release Checklist (in docs/RELEASE-CHECKLIST.md)](#3-release-checklist)
4. [GO-LIVE Check](#4-go-live-check)
5. [Post-Launch](#5-post-launch)
6. [Definition of Done](#definition-of-done)

---

## 1. Security Hardening

### 1.1 Penetration Testing Checklist

| # | Test | Tool / Method | Expected | Tester |
|---|------|---------------|----------|--------|
| PT-01 | SQL Injection (all endpoints) | SQLMap or manual payloads on `/v1/catalog`, `/v1/transactions` | No DB error leakage, all queries parameterized via Exposed/Room | |
| PT-02 | JWT token manipulation | Burp Suite / manual — tamper `alg`, extend `exp`, replay token | Token rejected, 401 returned | |
| PT-03 | JWT brute force (weak secret) | hashcat + rockyou on captured JWT | Secret ≥ 32 chars entropy; test fails | |
| PT-04 | JWT token refresh replay | Reuse refresh token after rotation | Old refresh token invalidated | |
| PT-05 | PIN brute force (rate limiting) | Automated PIN attempts (1000 req) | IP blocked after 10 failures in 300s (already implemented) | |
| PT-06 | Privilege escalation (horizontal) | Operator A tries to read Operator B's transactions | Route enforces ownership check | |
| PT-07 | Unauthorized endpoint access | Scan all routes without JWT header | 401 Unauthorized (except `/health`, `/v1/auth/login`) | |
| PT-08 | Path traversal (static files) | Payloads like `../../etc/passwd` on any file serving endpoint | No file disclosure | |
| PT-09 | gRPC reflection / introspection | `grpcurl -plaintext localhost:9000 list` | Should be disabled in production | |
| PT-10 | TLS version / cipher downgrade | `testssl.sh` or `nmap --script ssl-enum-ciphers` | TLS 1.2+ only, no weak ciphers | |
| PT-11 | HTTP security headers | `securityheaders.com` or `curl -I` | HSTS, X-Frame-Options, X-Content-Type-Options, CSP | |
| PT-12 | Docker container breakout | `docker run --privileged` test, read `/proc/1/root` | Non-root user (UID 1001), no privileged mode | |

### 1.2 Dependency Audit (OWASP)

| # | Task | Tool | Details |
|---|------|------|---------|
| DA-01 | Backend dependency scanning | OWASP Dependency-Check Gradle plugin | `org.owasp:dependency-check-gradle` — scan `backend/build.gradle.kts` |
| DA-02 | Android dependency scanning | OWASP Dependency-Check + Snyk | Check `android/build.gradle.kts` for CVEs |
| DA-03 | Docker base image scan | `docker scout` or `trivy image` | Scan `openjdk:21-jre-slim` for known vulns |
| DA-04 | Gradle wrapper SHA verification | Manual | Verify `gradle-wrapper.jar` SHA matches Gradle releases |
| DA-05 | Protobuf / gRPC library audit | Maven/Gradle version check | Ensure grpc-netty, protobuf-java, etc. are latest patch |
| DA-06 | Node/npm deps (if any) | `npm audit` or `yarn audit` | Frontend tooling deps (if present) |

**OWASP Dependency-Check Config (add to `backend/build.gradle.kts`):**
```kotlin
plugins {
    id("org.owasp.dependencycheck") version "10.0.4"
}
dependencyCheck {
    failBuildOnCVSS = 7.0
    suppressionFile = file("dependency-check-suppressions.xml")
}
```

### 1.3 Secrets Rotation

| # | Action | Owner | Before / After Launch |
|---|--------|-------|-----------------------|
| SR-01 | Generate new JWT secret (64+ chars) | Lead Dev | **Before** deployment |
| SR-02 | Generate new DB password (32+ chars) | Lead Dev | **Before** deployment |
| SR-03 | Generate new Redis password (32+ chars) | Lead Dev | **Before** deployment |
| SR-04 | Generate new HMAC signing key | Lead Dev | **Before** deployment |
| SR-05 | Rotate Play Store signing key (if compromised) | Lead Dev | Only if needed |
| SR-06 | Store secrets in GitHub Actions secrets | Lead Dev | **Before** first CI run |

**Secret generation commands:**
```bash
# Generate 64-char random secrets
openssl rand -base64 48   # JWT secret
openssl rand -base64 32   # DB password
openssl rand -base64 32   # Redis password
openssl rand -hex 32      # HMAC key
```

### 1.4 SSL/TLS Verification

| # | Check | Tool | Criterion |
|---|-------|------|-----------|
| SSL-01 | Certificate validity | `openssl s_client -connect api.taut.app:443` | Not expired, correct CN/SAN |
| SSL-02 | TLS version | `nmap --script ssl-enum-ciphers -p 443 api.taut.app` | ≥ TLS 1.2, TLS 1.3 preferred |
| SSL-03 | Certificate chain | `openssl verify -CAfile chain.pem cert.pem` | Full chain → trusted root |
| SSL-04 | HSTS header | `curl -sI https://api.taut.app \| grep -i strict-transport` | `max-age=31536000; includeSubDomains` |
| SSL-05 | OCSP stapling | `openssl s_client -status -connect api.taut.app:443` | OCSP response present |
| SSL-06 | gRPC TLS (Android -> backend) | gRPC debug logging | Uses `useTransportSecurity()` in release builds |
| SSL-07 | Let's Encrypt auto-renewal | `certbot renew --dry-run` | Renewal cron works |

---

## 2. Production Deployment

### 2.1 VPS Provisioning Checklist

**Recommended Spec:**
| Resource | Minimum | Recommended |
|----------|---------|-------------|
| CPU | 2 vCPU | 4 vCPU |
| RAM | 4 GB | 8 GB |
| Storage | 40 GB SSD | 80 GB SSD |
| OS | Ubuntu 22.04 LTS | Ubuntu 24.04 LTS |
| Network | 1 Gbps, public IP | 1 Gbps, static IP |

| # | Step | Command / Reference |
|---|------|---------------------|
| VP-01 | Create VPS (DigitalOcean / Vultr / GCP) | Region: **asia-southeast2** (Jakarta) for UU PDP compliance |
| VP-02 | SSH key setup | `ssh-copy-id root@<VPS_IP>` |
| VP-03 | Disable root password login | Edit `/etc/ssh/sshd_config`: `PasswordAuthentication no` |
| VP-04 | Create deploy user | `adduser deploy && usermod -aG docker deploy` |
| VP-05 | Install Docker 24+ | `curl -fsSL https://get.docker.com \| sh` |
| VP-06 | Install Docker Compose plugin | `apt install docker-compose-plugin` |
| VP-07 | Configure UFW firewall | `ufw allow 22/tcp && ufw allow 80/tcp && ufw allow 443/tcp && ufw enable` |
| VP-08 | Install fail2ban | `apt install fail2ban && systemctl enable fail2ban` |
| VP-09 | Set up swap (if RAM < 4GB) | `fallocate -l 2G /swapfile && chmod 600 /swapfile && mkswap /swapfile && swapon /swapfile` |
| VP-10 | Configure Docker daemon JSON | Set `log-driver: json-file`, `log-opts.max-size: 10m`, `log-opts.max-file: 3` |
| VP-11 | Kernel hardening via sysctl | `net.ipv4.tcp_syncookies=1`, `fs.protected_hardlinks=1`, etc. |

### 2.2 Domain + SSL Setup

| # | Step | Details |
|---|------|---------|
| DS-01 | Register domain (e.g., `taut.app`) | Namecheap / Cloudflare / Niagahoster |
| DS-02 | Point A record to VPS IP | `api.taut.app → <VPS_IP>` |
| DS-03 | Point A record for Android | `api.taut.app` (single endpoint) |
| DS-04 | Install Nginx / Caddy as reverse proxy | Caddy recommended (auto-SSL, simpler config) |
| DS-05 | Configure Caddyfile | Reverse proxy to `localhost:8080` |
| DS-06 | Obtain SSL cert via Let's Encrypt | Caddy auto-handles this |
| DS-07 | Set up auto-renewal | Caddy auto-renew; Nginx: `certbot renew --cron` |
| DS-08 | Verify SSL with testssl.sh | See SSL-01 to SSL-06 above |

**Example Caddyfile:**
```caddyfile
api.taut.app {
    reverse_proxy localhost:8080
    header / Strict-Transport-Security "max-age=31536000; includeSubDomains"
    header / X-Content-Type-Options "nosniff"
    header / X-Frame-Options "DENY"
    header / Content-Security-Policy "default-src 'self'"
    log {
        output file /var/log/caddy/api.taut.app.log
    }
}
```

### 2.3 Docker Deployment Runbook

**Prerequisites:**
- VPS provisioned (Section 2.1)
- Domain + SSL configured (Section 2.2)
- `.env.production` file prepared with real secrets
- Docker installed on VPS

**Runbook Steps:**

```bash
# ── 1. Clone repository on VPS ──
git clone git@github.com:<org>/taut.git /opt/taut
cd /opt/taut

# ── 2. Create production .env ──
cp .env.template .env.production
# Edit .env.production with real secrets
nano .env.production

# ── 3. Pull & build images ──
docker compose -f docker/docker-compose.yml pull
docker compose -f docker/docker-compose.yml build

# ── 4. Start services ──
docker compose -f docker/docker-compose.yml up -d

# ── 5. Verify health ──
docker compose ps
curl -f http://localhost:8080/health

# ── 6. Check logs ──
docker compose logs -f backend

# ── 7. Run smoke tests ──
# (See GO-LIVE section)
```

**Docker Compose Override for Production (`docker/docker-compose.prod.yml`):**
```yaml
services:
  backend:
    restart: always
    deploy:
      resources:
        limits:
          memory: 768M
          cpus: "1.0"
    logging:
      driver: "json-file"
      options:
        max-size: "10m"
        max-file: "3"
```

**Rollback procedure:** See Section 4.2.

### 2.4 Monitoring (Prometheus / Grafana — Optional)

**Why optional:** For initial launch, health endpoint + Docker logs are sufficient. Prometheus/Grafana can be added post-launch.

**If implemented, recommended stack:**

| Service | Image | Purpose |
|---------|-------|---------|
| Prometheus | `prom/prometheus:latest` | Metrics collection from backend `/metrics` (Micrometer) |
| Grafana | `grafana/grafana:latest` | Dashboards + alerting |
| Loki + Promtail | `grafana/loki:latest` | Log aggregation |
| cAdvisor | `gcr.io/cadvisor/cadvisor:latest` | Container resource metrics |

**Minimal docker-compose addition:**

```yaml
prometheus:
  image: prom/prometheus:latest
  ports: ["9090:9090"]
  volumes: ["./prometheus.yml:/etc/prometheus/prometheus.yml"]

grafana:
  image: grafana/grafana:latest
  ports: ["3000:3000"]
  environment:
    GF_SECURITY_ADMIN_PASSWORD: ${GRAFANA_PASSWORD:-admin}
  depends_on: [prometheus]
```

**Health endpoint** (already implemented at `/health`):
```json
{
  "status": "healthy",
  "version": "1.0.0",
  "uptime_seconds": 3600,
  "database": "connected",
  "redis": "connected"
}
```

---

## 3. Release Checklist

> **Full document:** [`docs/RELEASE-CHECKLIST.md`](./RELEASE-CHECKLIST.md)  
> This section is a summary of the release flow.

| Phase | Key Tasks | Owner |
|-------|-----------|-------|
| **A. Version Bump** | `v1.0.0` → `v1.1.0` in build.gradle.kts + Android versionCode bump | Lead Dev |
| **B. Changelog** | Generate from git log since last tag, categorize (Features, Fixes, Security) | Lead Dev |
| **C. APK Signing** | Generate/use Keystore, sign APK, run `zipalign`, verify signature | Android Dev |
| **D. Play Store** | Internal Test → Closed Track → Open Beta → Production | PM |
| **E. Git Tag** | `git tag v1.0.0 && git push origin v1.0.0` | Lead Dev |

**Version Bump SOP:**
```
Current: v1.0.0 (Sprint 4 → v1.0.0 is initial production release)
```

**APK signing quick reference:**
```bash
# Generate keystore (first time only)
keytool -genkey -v -keystore taut-release.keystore \
  -alias taut -keyalg RSA -keysize 2048 -validity 10000

# Build signed APK
cd android/
./gradlew assembleRelease

# Verify signature
jarsigner -verify -verbose -certs app/build/outputs/apk/release/app-release.apk
```

---

## 4. GO-LIVE Check

### 4.1 Backup Verification

| # | Check | Criteria |
|---|-------|----------|
| BV-01 | PostgreSQL backup script exists & executable | `scripts/backup-db.sh` runs without error |
| BV-02 | Backup actually produces valid `.sql.gz` file | `gunzip -t backup_*.sql.gz` succeeds |
| BV-03 | Backup restore tested | `psql -f backup.sql` → `pg_restore` test on staging DB |
| BV-04 | Backup schedule configured (cron) | `crontab -e`: daily at 03:00 WIB |
| BV-05 | Off-site backup configured | `rclone copy` to Google Drive / S3 (optional) |
| BV-06 | Docker volumes backup understood | `tar -czf pgdata.tar.gz /var/lib/docker/volumes/taut-pgdata/` |

### 4.2 Rollback Plan

| Scenario | Detection | Action | RTO (Recovery Time) |
|----------|-----------|--------|---------------------|
| Backend crash on startup | `docker ps` shows `restarting` | `git revert` last deploy, `docker compose down && docker compose up -d` | ~5 min |
| Database migration failure | Backend logs migration error | Restore backup, pin backend to previous image | ~15 min |
| Security vulnerability found | Pen test or CVE alert | Hotfix → force push → CI deploy → notify users | ~2 hrs |
| Android app crash on launch | Play Store crash reports > 1% | Roll back Play Store release to previous APK | ~30 min |
| Data corruption | Angular monitoring reports inconsistency | Restore DB from backup, re-sync Android devices | ~1 hr |

**Rollback procedure step-by-step (backend):**
```bash
# 1. Identify the last known-good image or commit
docker images | grep taut-backend
git log --oneline -5

# 2. If using git revert:
git revert HEAD --no-edit
git push origin main

# 3. If using previous Docker image:
docker compose -f docker/docker-compose.yml down
docker compose -f docker/docker-compose.yml up -d backend:<previous-tag>

# 4. Restore DB if needed:
gunzip -c /backups/taut-pre-deploy.sql.gz | psql -U taut -d taut

# 5. Verify health:
curl -f http://localhost:8080/health

# 6. Notify team via Slack/WhatsApp
```

### 4.3 Smoke Test Procedure

Run these **immediately after deployment** — automated via CI or manual:

```bash
#!/bin/bash
# smoke-test.sh — Run after deployment
set -e

HOST="${1:-http://localhost:8080}"
PASS=0; FAIL=0

check() {
  local desc="$1"; local expected="$2"; local actual="$3"
  if [ "$actual" = "$expected" ]; then
    echo "  ✅ $desc"; ((PASS++))
  else
    echo "  ❌ $desc (expected: $expected, got: $actual)"; ((FAIL++))
  fi
}

echo "=== Smoke Tests ==="

# ST-01: Health endpoint returns 200
STATUS=$(curl -s -o /dev/null -w "%{http_code}" "$HOST/health")
check "Health endpoint returns 200" "200" "$STATUS"

# ST-02: Health response is valid JSON
BODY=$(curl -s "$HOST/health")
echo "$BODY" | jq . > /dev/null 2>&1
check "Health response is valid JSON" "0" "$?"

# ST-03: status field is "healthy"
STATUS_VAL=$(echo "$BODY" | jq -r '.status')
check "status = healthy" "healthy" "$STATUS_VAL"

# ST-04: database is connected
DB_VAL=$(echo "$BODY" | jq -r '.database')
check "database = connected" "connected" "$DB_VAL"

# ST-05: Unauthenticated request to protected endpoint returns 401
STATUS=$(curl -s -o /dev/null -w "%{http_code}" "$HOST/v1/transactions")
check "Protected endpoint returns 401" "401" "$STATUS"

# ST-06: Login endpoint returns 400 or 200 (exists)
STATUS=$(curl -s -o /dev/null -w "%{http_code}" -X POST "$HOST/v1/auth/login" \
  -H "Content-Type: application/json" -d '{"pin":"0000"}')
check "Login endpoint responds" "400" "$STATUS"

# ST-07: API returns reasonable response time
START=$(date +%s%N)
curl -sf "$HOST/health" > /dev/null 2>&1
END=$(date +%s%N)
MS=$(( (END - START) / 1000000 ))
if [ "$MS" -lt 1000 ]; then R="fast"; else R="slow"; fi
check "Response time < 1s (${MS}ms)" "fast" "$R"

echo ""
echo "Results: $PASS passed, $FAIL failed"
[ "$FAIL" -eq 0 ] && echo "✅ ALL SMOKE TESTS PASSED" || echo "❌ SOME TESTS FAILED"
exit $FAIL
```

**Manual smoke test checklist for GO-LIVE day:**

- [ ] ST-01: Health endpoint accessible via public domain (`https://api.taut.app/health`)
- [ ] ST-02: Backend logs show no errors after 5 minutes of uptime
- [ ] ST-03: Register new operator → login → create transaction → verify sync
- [ ] ST-04: Install release APK on Android device → PIN login → create transaction
- [ ] ST-05: Turn off internet on Android → create offline transaction → reconnect → verify sync
- [ ] ST-06: Verify backup ran successfully (`ls -la /backups/`)
- [ ] ST-07: Load test: 10 concurrent requests to `/health` respond within 500ms

### 4.4 Health Endpoint Monitoring

| Check | Method | Alert If |
|-------|--------|----------|
| Uptime | External pingdom / uptimerobot / cron job | Down for > 5 min |
| Health | `curl https://api.taut.app/health` every 5 min | status ≠ "healthy" |
| DB connection | Health endpoint `.database` field | "disconnected" |
| Redis connection | Health endpoint `.redis` field (if added) | "disconnected" |
| SSL expiry | `certbot renew --dry-run` weekly | Expires in < 30 days |
| Disk usage | `df -h` on VPS (cron alert) | > 80% |
| Memory usage | `free -m` (cron alert) | > 90% |

**Simple monitoring script (bash + cron):**
```bash
#!/bin/bash
# /opt/taut/scripts/monitor-health.sh
HEALTH=$(curl -sf https://api.taut.app/health)
if [ $? -ne 0 ]; then
  echo "TAUT DOWN - $(date)" >> /var/log/taut-monitor.log
  curl -X POST -H "Content-Type: application/json" \
    -d '{"text":"🚨 TAUT is DOWN!"}' \
    <webhook-url>
fi
```

---

## 5. Post-Launch

### 5.1 Bug Tracker Priority

After launch, bugs triaged by severity:

| Priority | Label | Response Time | Fix Target | Example |
|----------|-------|---------------|------------|---------|
| 🔴 **P0 — Critical** | `critical` | Within 4 hours | Hotfix within 24h | Data loss, auth broken, API down |
| 🟠 **P1 — High** | `major` | Within 24 hours | Next patch (≤ 3 days) | Transaction not syncing, crash on specific screen |
| 🔵 **P2 — Medium** | `medium` | Within 72 hours | Next sprint | UI glitch, missing validation, performance slow |
| 🟢 **P3 — Low** | `low` | 1 week | Backlog / next minor | Typo, cosmetic, nice-to-have enhancement |

**Bug reporting template:**
```markdown
## Bug Report
**Priority:** P0/P1/P2/P3
**Device/OS:** [e.g., Redmi A2 / Android 13]
**App Version:** [e.g., 1.0.0]
**Steps to reproduce:**
1. Go to ...
2. Tap ...
3. See error

**Expected behavior:**
**Actual behavior:**
**Screenshots/logs:**
```

### 5.2 Feature Request Pipeline

| Stage | Gatekeeper | Process |
|-------|-----------|---------|
| 1. Submit | All stakeholders (ops, dev, users) | Submit via GitHub Issues / internal form |
| 2. Triage | PM | Label as `feature-request`, assign rough priority |
| 3. Review | PM + Lead Dev | Estimate effort (T-shirt size: S/M/L/XL), validate alignment with roadmap |
| 4. Approve | CEO / Product Owner | Add to `backlog` milestone |
| 5. Schedule | PM | Assign to sprint during planning |
| 6. Build | Dev | Follow feature branch workflow |
| 7. Review | QA + PM | Acceptance testing on staging |
| 8. Deploy | DevOps | Merge to `main`, tag release |

**Initial feature backlog (suggestions for v1.1.0):**
- [ ] SMS notification integration (Twilio)
- [ ] gRPC health check service (fix current hardcoded `true`)
- [ ] Admin dashboard web UI (React/Vue)
- [ ] Export transactions to CSV/Excel
- [ ] Multi-branch support (multiple waste bank locations)
- [ ] Customer-facing balance check (via QR code / SMS)
- [ ] Prometheus metrics endpoint (`/metrics`)
- [ ] Rate limiting per-operator (not just per-IP)

### 5.3 Team Retrospektif

**Format:** 45-minute async or synchronous meeting, 1 week after launch.

**Agenda:**
1. **What went well?** 🎉 — Things to celebrate and continue
2. **What could be improved?** 🔧 — Process, communication, technical debt
3. **What was surprising?** 🤔 — Unexpected challenges or discoveries
4. **Action items** 🎯 — 3 concrete improvements for next sprint

**Retro questions:**
- Were the sprint estimates accurate? If not, what threw them off?
- Was the Definition of Done followed consistently?
- Did the CI/CD pipeline catch any issues before deployment?
- How was the GO-LIVE day experience? What would we do differently?
- Is the monitoring sufficient? What gaps were exposed?

**Output:** A brief retro doc (`docs/retro-sprint4.md`) with action items tracked as GitHub Issues.

---

## Definition of Done

> Every item below must be checked before declaring Sprint 4 complete.

### Security
- [ ] Penetration testing checklist fully executed, no CRITICAL findings
- [ ] OWASP dependency scan run, no CVSS ≥ 7.0 vulnerabilities
- [ ] All production secrets rotated and stored in GitHub Secrets
- [ ] SSL/TLS verified (TLS 1.2+, valid cert, HSTS configured)
- [ ] Rate limiting verified on PIN endpoint (10/300s)

### Deployment
- [ ] VPS provisioned and hardened (UFW, fail2ban, non-root user)
- [ ] Domain DNS configured, SSL via Let's Encrypt
- [ ] Docker compose runs successfully (backed + Postgres + Redis)
- [ ] Health endpoint accessible via public domain
- [ ] Smoke tests pass (all 7+ checks)
- [ ] Backup script verified (run + restore tested)

### Release
- [ ] Version bumped to `v1.0.0`
- [ ] CHANGELOG.md written
- [ ] APK signed with release keystore
- [ ] APK uploaded to Play Store (Internal Test track minimum)
- [ ] Git tag `v1.0.0` pushed
- [ ] GitHub release created with changelog

### GO-LIVE
- [ ] Rollback plan documented and understood by team
- [ ] Monitoring (cron/pingdom) active
- [ ] Load test: 10 concurrent users → all respond within 2s
- [ ] Post-launch bug tracker configured
- [ ] Retro scheduled for 1 week post-launch

---

## Timeline (10 Hari Kerja)

| Day | Focus | Key Deliverables |
|-----|-------|------------------|
| **1–2** | Security Hardening | PT run, OWASP scan, secrets rotation, SSL setup |
| **3–4** | VPS Provisioning + Docker | VPS setup, Docker compose tested, domain + SSL |
| **5** | CI/CD Finalize | GitHub Actions tested, docker-build workflow working |
| **6–7** | Release Prep | Version bump, changelog, APK signing, Play Store upload |
| **8** | GO-LIVE Dry Run | Full rehearsal: smoke test, backup, rollback |
| **9** | **🚀 GO-LIVE DAY** | Deploy, verify, monitor, announce |
| **10** | Post-Launch | Bug triage, retro, first patch if needed |

---

> **Document Status:** Draft v1.0 — Finalize with team during Sprint 4 kickoff.  
> **Author:** Hermes Agent Sprint 4 Planning  
> **Date:** 26 June 2026
