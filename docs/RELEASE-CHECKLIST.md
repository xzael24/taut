# TAUT v1.0.0 — Release Checklist

> **Version:** 1.0.0  
> **Date:** 26 June 2026  
> **Status:** 🟡 Pending — Begin executing on Sprint 4 Day 6  

---

## Overview

This document is the **authoritative checklist** for shipping TAUT v1.0.0 to production.  
Each section has owners, deadlines, and acceptance criteria. **All items must be ✅ before GO-LIVE.**

---

## Pre-Release: Code Freeze (Day 5)

| # | Task | Status | Owner | Notes |
|---|------|--------|-------|-------|
| PR-01 | Feature freeze on `main` branch | ☐ | Lead Dev | No new features after Day 5 |
| PR-02 | All tests passing (backend 10/10 + Android 70/70) | ☐ | Lead Dev | Run locally, verify CI green |
| PR-03 | No open CRITICAL or MAJOR bugs on `main` | ☐ | QA | Triage all open issues |
| PR-04 | Code review completed for all merged PRs | ☐ | Lead Dev | Check GitHub PR history |
| PR-05 | `develop` branch merged into `main` | ☐ | Lead Dev | Final integration merge |

---

## Phase A: Version Bump (Day 6)

| # | Task | Status | Details |
|---|------|--------|---------|
| A-01 | Bump `versionName` in `android/app/build.gradle.kts` | ☐ | From `1.0.0-dev` to `1.0.0` |
| A-02 | Bump `versionCode` in `android/app/build.gradle.kts` | ☐ | Must be higher than any previous upload |
| A-03 | Bump `version` in `backend/build.gradle.kts` | ☐ | From `1.0.0-dev` to `1.0.0` |
| A-04 | Bump `version` in `docker/Dockerfile` comment | ☐ | Update version label in comments |
| A-05 | Verify version grep | ☐ | `grep -rn "1.0.0-dev" --include="*.kts" --include="*.gradle"` returns empty |

**Commands:**
```bash
# Verify no dev versions remain
grep -rn "1.0.0-dev" android/app/build.gradle.kts backend/build.gradle.kts

# Verify build.gradle.kts has correct version
grep "versionName" android/app/build.gradle.kts
grep "version " backend/build.gradle.kts
```

---

## Phase B: Changelog (Day 6)

| # | Task | Status | Details |
|---|------|--------|---------|
| B-01 | Generate git log since last tag | ☐ | `git log v0.9.0..HEAD --oneline` (or initial) |
| B-02 | Categorize entries | ☐ | Features, Bug Fixes, Security, Breaking Changes |
| B-03 | Write `CHANGELOG.md` entry for v1.0.0 | ☐ | Use [Keep a Changelog](https://keepachangelog.com) format |
| B-04 | Proofread changelog | ☐ | PM review |
| B-05 | Commit `CHANGELOG.md` | ☐ | `git add CHANGELOG.md && git commit -m "docs: add v1.0.0 changelog"` |

**CHANGELOG template entry:**
```markdown
# Changelog

## [1.0.0] - 2026-06-XX

### 🚀 Features
- Offline-first transaction recording for waste bank operations
- gRPC bidirectional sync between devices and backend
- SQLCipher AES-256-GCM encryption at rest
- PIN-based authentication with rate limiting
- Push notification system
- Auto-sync scheduler (WorkManager)
- Persistent authentication with secure token storage

### 🐛 Bug Fixes
- Fixed gRPC port mismatch between Android and backend
- Fixed monetary display 100x inflated (Rp formatting)
- Fixed SyncWorker data loss on gRPC failure
- Fixed duplicate Flyway migration issue
- Fixed CORS wildcard origin vulnerability
- Fixed rate limiting on PIN verification endpoint

### 🔒 Security
- Added HMAC-SHA256 verification for transactions
- Added IP-based rate limiting (10 req/300s)
- Fixed pin_salt empty string vulnerability
- Added defensive copy of passphrase before zeroing
- CORS restricted to configurable origins
- UU PDP compliance endpoints (export, forget, consent)

### 📦 Infrastructure
- Multi-stage Docker build (JDK 21 build, JRE 21 runtime)
- Non-root container user (taut, UID 1001)
- PostgreSQL 16 with Flyway migrations
- Redis 7 for queue and caching
- GitHub Actions CI pipeline

### ⚠️ Known Issues
- gRPC health check returns hardcoded `true` (planned for v1.1.0)
- SMS notifications not yet integrated (Twilio pending)
- Docker Desktop required for local container testing
```

---

## Phase C: APK Signing + Play Store (Days 6–7)

| # | Task | Status | Details |
|---|------|--------|---------|
| C-01 | Generate release keystore (if first release) | ☐ | `keytool -genkey -v -keystore taut-release.keystore ...` |
| C-02 | Backup keystore + credentials to secure location | ☐ | **DO NOT** commit to git. Store in password manager |
| C-03 | Configure `signingConfigs` in `build.gradle.kts` | ☐ | Reference keystore path (local) or CI secrets |
| C-04 | Build release APK/AAB | ☐ | `./gradlew bundleRelease` (AAB for Play Store) |
| C-05 | Verify APK signature | ☐ | `jarsigner -verify -verbose app/build/outputs/bundle/release/*.aab` |
| C-06 | Test APK on physical device | ☐ | Install AAB on 2+ real devices (one low-end if possible) |
| C-07 | Generate Play Store signing key (if using Play App Signing) | ☐ | Via Google Play Console |
| C-08 | Upload AAB to Play Store — Internal Test track | ☐ | Add internal testers, verify download |
| C-09 | Expand to Closed Beta track | ☐ | After 24h internal testing passes |
| C-10 | Prepare Store Listing | ☐ | App name, description, screenshots, feature graphic |
| C-11 | Prepare Privacy Policy page | ☐ | Required by Google Play for apps handling user data |
| C-12 | Submit for production review | ☐ | This can take 1–7 days with Google |

**APK signing commands:**
```bash
# ── Build AAB (Android App Bundle) ──
cd android/
./gradlew bundleRelease
# Output: app/build/outputs/bundle/release/app-release.aab

# ── Verify signature ──
jarsigner -verify -verbose -certs \
  app/build/outputs/bundle/release/app-release.aab

# ── Generate APK from AAB (for manual testing) ──
# Use Google's `bundletool` or build debug for testing
./gradlew assembleDebug
# Output: app/build/outputs/apk/debug/app-debug.apk
```

**Google Play Console checklist:**
- [ ] App category: "Productivity" or "Lifestyle"
- [ ] Content rating: "Everyone" (no violence, no adult content)
- [ ] Privacy policy URL: `https://taut.app/privacy` (or hosted page)
- [ ] Data safety form: Declare SQLCipher local storage, gRPC network
- [ ] App icon: 512x512 PNG (Play Store listing)
- [ ] Screenshots: Minimum 2 phone screenshots
- [ ] Feature graphic: 1024x500 JPG/PNG

---

## Phase D: Backend Deployment (Day 8)

| # | Task | Status | Details |
|---|------|--------|---------|
| D-01 | VPS provisioned (Section 2.1 of Sprint 4 Plan) | ☐ | Ubuntu 24.04 LTS, 4GB+ RAM, Docker installed |
| D-02 | SSH key access verified | ☐ | `ssh deploy@<VPS_IP>` works |
| D-03 | `.env.production` created on VPS with real secrets | ☐ | All secrets generated, no placeholder values |
| D-04 | Docker image built and tagged | ☐ | `docker build -t taut-backend:v1.0.0 .` |
| D-05 | Image pushed to registry (or copied to VPS) | ☐ | `docker save/load` or GHCR/Docker Hub |
| D-06 | Docker compose up on VPS | ☐ | `docker compose -f docker/docker-compose.yml up -d` |
| D-07 | Health endpoint responding on VPS | ☐ | `curl -f http://localhost:8080/health` on VPS |
| D-08 | Domain pointing to VPS | ☐ | `dig api.taut.app` returns VPS IP |
| D-09 | SSL cert valid on public domain | ☐ | `curl -fI https://api.taut.app/health` returns 200 |
| D-10 | Caddy/Nginx reverse proxy running | ☐ | HTTP → HTTPS redirect working |

**Docker image build + push commands:**
```bash
# On dev machine
cd backend/
docker build -t ghcr.io/<org>/taut-backend:v1.0.0 .
docker tag ghcr.io/<org>/taut-backend:v1.0.0 ghcr.io/<org>/taut-backend:latest
docker push ghcr.io/<org>/taut-backend:v1.0.0
docker push ghcr.io/<org>/taut-backend:latest

# On VPS
docker pull ghcr.io/<org>/taut-backend:v1.0.0
docker compose -f docker/docker-compose.yml pull
docker compose -f docker/docker-compose.yml up -d
```

**Alternative (no registry — copy image via SSH):**
```bash
# On dev machine: save image to tarball
docker save ghcr.io/<org>/taut-backend:v1.0.0 | gzip > taut-backend-v1.0.0.tar.gz

# Copy to VPS
scp taut-backend-v1.0.0.tar.gz deploy@<VPS_IP>:/opt/taut/

# On VPS: load image and start
cd /opt/taut
docker load < taut-backend-v1.0.0.tar.gz
docker compose -f docker/docker-compose.yml up -d
```

---

## Phase E: Git Tag + Release (Day 8)

| # | Task | Status | Details |
|---|------|--------|---------|
| E-01 | Ensure all tests pass locally | ☐ | `./gradlew test` (backend) + `./gradlew testDebugUnitTest` (android) |
| E-02 | Ensure CI is green on `main` | ☐ | Check GitHub Actions: `.github/workflows/ci.yml` |
| E-03 | Create annotated git tag | ☐ | `git tag -a v1.0.0 -m "Release v1.0.0 - Production"` |
| E-04 | Push tag | ☐ | `git push origin v1.0.0` |
| E-05 | Create GitHub Release | ☐ | `gh release create v1.0.0 --title "v1.0.0" --notes-file CHANGELOG.md` |
| E-06 | Attach release artifacts (if any) | ☐ | Signed APK/AAB if distributing via GitHub |

**Git tag commands:**
```bash
# Ensure clean working tree
git status
git stash  # if needed

# Create annotated tag
git tag -a v1.0.0 -m "Release v1.0.0 — Production

Features:
- Offline-first transaction recording
- gRPC bidirectional sync
- SQLCipher encryption at rest
- PIN authentication with rate limiting
- Push notifications
- Auto-sync scheduler

Security:
- HMAC-SHA256 transaction verification
- IP-based rate limiting
- CORS restriction
- UU PDP compliance"

# Push tag
git push origin v1.0.0

# Create GitHub Release (via gh CLI)
gh release create v1.0.0 \
  --title "TAUT v1.0.0 — Production Release" \
  --notes-file CHANGELOG.md
```

---

## Phase F: GO-LIVE Day (Day 9)

| # | Task | Status | Time | Details |
|---|------|--------|------|---------|
| F-01 | Final backup of staging/dev data | ☐ | 08:00 | `scripts/backup-db.sh` |
| F-02 | Deploy Docker compose to production VPS | ☐ | 09:00 | Section D runbook |
| F-03 | Verify health endpoint (public) | ☐ | 09:15 | `curl -f https://api.taut.app/health` |
| F-04 | Run smoke test script | ☐ | 09:20 | `bash smoke-test.sh https://api.taut.app` |
| F-05 | Manual smoke test (create transaction) | ☐ | 09:30 | Register operator → login → create transaction → verify |
| F-06 | Test on physical Android device | ☐ | 09:45 | Install APK → PIN login → create transaction → verify sync |
| F-07 | Verify backup cron is set on VPS | ☐ | 10:00 | `crontab -l` shows daily backup at 03:00 |
| F-08 | Monitor for 30 minutes | ☐ | 10:00–10:30 | Watch `docker logs -f taut-backend` for errors |
| F-09 | Announce launch | ☐ | 10:30 | Notify stakeholders via WhatsApp/Slack/Email |
| F-10 | Update project README with version | ☐ | 11:00 | Change version references to v1.0.0 |

**GO-LIVE health check URLs:**
```
Backend health:  https://api.taut.app/health
Backend root:    https://api.taut.app/
Play Store:      https://play.google.com/store/apps/details?id=com.taut.app
```

---

## Phase G: Post-Launch (Day 10 + Ongoing)

| # | Task | Status | When | Details |
|---|------|--------|------|---------|
| G-01 | Monitor crash reports (Firebase Crashlytics) | ☐ | Ongoing | 1 hour after launch |
| G-02 | Monitor Play Store reviews | ☐ | Daily | Respond to negative reviews within 24h |
| G-03 | Verify backup was created overnight | ☐ | Day 10, morning | `ls -la /opt/taut/backups/` |
| G-04 | Check server resource usage | ☐ | Day 10 | CPU, RAM, disk, network — all within limits |
| G-05 | Team retrospective | ☐ | Day 15 | 1-week post-launch review |
| G-06 | File any P0/P1 bugs found post-launch | ☐ | As needed | Use bug tracker template |
| G-07 | Plan v1.1.0 backlog | ☐ | Day 15 | Include SMS integration, gRPC health, metrics endpoint |
| G-08 | Update `docs/SPRINT-0-PLAN.md` with final status | ☐ | Day 15 | Record lessons learned |

---

## Emergency Contacts

| Role | Name | Contact | Responsibility |
|------|------|---------|----------------|
| Lead Developer | [TBD] | [TBD] | Technical decisions, rollback authority |
| PM | [TBD] | [TBD] | Stakeholder communication, bug prioritization |
| VPS / DevOps | [TBD] | [TBD] | Server issues, Docker, SSL |

---

## Rollback Quick Reference

**If backend is broken within 30 minutes of deployment:**

```bash
# 1. STOP the broken deployment
cd /opt/taut
docker compose -f docker/docker-compose.yml down

# 2. RESTORE previous image (if saved)
docker load < /opt/taut/backups/taut-backend-v0.9.0.tar.gz
docker compose -f docker/docker-compose.yml up -d

# 3. RESTORE database (if migration failed)
gunzip -c /opt/taut/backups/taut-pre-deploy-$(date +%Y%m%d).sql.gz | \
  psql -U taut -d taut

# 4. VERIFY health
curl -f http://localhost:8080/health

# 5. NOTIFY team
# Send message: "Rolled back to v0.9.0 due to [reason]. Investigating."
```

---

## Sign-Off

| Role | Name | Signature | Date |
|------|------|-----------|------|
| Lead Developer | | | |
| QA | | | |
| PM | | | |
| CEO / Product Owner | | | |

> **All checkboxes above must be ✅ before GO-LIVE on Day 9.**  
> If any P0 or P1 issue is found during GO-LIVE, execution stops and rollback is triggered.

---

> **Document Version:** 1.0  
> **Author:** Hermes Agent Sprint 4 Planning  
> **Date:** 26 June 2026  
> **Status:** 🟡 Ready for Sprint 4 kickoff
