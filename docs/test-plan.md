# TAUT — Comprehensive Integration Test Plan

**Project:** TAUT — Bank Sampah Digital  
**Version:** 1.0  
**Date:** 26 Juni 2026  
**Scope:** Backend integration tests, gRPC sync flow, auth flow, security hardening

---

## Table of Contents

1. [Test Architecture Overview](#1-test-architecture-overview)
2. [API Endpoint Integration Tests](#2-api-endpoint-integration-tests)
   - [Auth Routes](#21-auth-routes)
   - [Transaction Routes](#22-transaction-routes)
   - [Catalog Routes](#23-catalog-routes)
   - [Dashboard Routes](#24-dashboard-routes)
   - [Device Routes](#25-device-routes)
   - [Compliance Routes](#26-compliance-routes)
   - [SMS Routes](#27-sms-routes)
   - [Sync Routes](#28-sync-routes)
   - [Health Endpoint](#29-health-endpoint)
3. [Sync Flow Test Plan (gRPC)](#3-sync-flow-test-plan-grpc)
   - [Bidirectional Stream Flow](#31-bidirectional-stream-flow)
   - [Unary Sync Flow](#32-unary-sync-flow)
   - [Conflict Resolution](#33-conflict-resolution)
   - [Offline Queue Tests](#34-offline-queue-tests)
4. [Auth Flow Test Plan](#4-auth-flow-test-plan)
   - [JWT Token Lifecycle](#41-jwt-token-lifecycle)
   - [PIN Verification Flow](#42-pin-verification-flow)
   - [OTP Flow Tests](#43-otp-flow-tests)
5. [Security Test Plan](#5-security-test-plan)
   - [Rate Limiting](#51-rate-limiting)
   - [CORS Configuration](#52-cors-configuration)
   - [Input Validation](#53-input-validation)
   - [Authentication & Authorization](#54-authentication--authorization)
   - [Security Headers](#55-security-headers)
6. [Test Execution Guide](#6-test-execution-guide)
7. [Test Coverage Matrix](#7-test-coverage-matrix)

---

## 1. Test Architecture Overview

### 1.1 Testing Stack

| Component | Technology | Purpose |
|-----------|-----------|---------|
| **Test Framework** | JUnit 5 (Jupiter) | Test lifecycle, assertions, parameterized tests |
| **Ktor Test Host** | `testApplication {}` | In-process HTTP server for route testing |
| **Mocking** | MockK | Service-level mocking (no DB/gRPC dependencies) |
| **HTTP Client** | Ktor client bound to test host | `createClient { }` for request/response testing |

### 1.2 Key Principles

1. **No external dependencies** — all tests use `mockkObject()` or `mockk()` for service layers
2. **No database connection** — every `AuthService`, `TransactionService`, etc. is mocked
3. **No gRPC server** — sync endpoints are tested as REST stubs (returning 501 NotImplemented)
4. **Real JWT verification** — tests generate real JWTs signed with test secret to exercise `configureSecurity()`
5. **Test isolation** — `@BeforeEach`/`@AfterEach` (or PER_CLASS with setup/teardown) ensures clean state

### 1.3 Base Config Template

```kotlin
val jwtSecret = "test-jwt-secret-32-chars-minimum!!"
val testConfig = AppConfig(
    server = ServerConfig("0.0.0.0", 8080),
    database = DatabaseConfig("test-url", "test", "test", 5, 1, 300000, 30000, 1800000),
    jwt = JwtConfig(jwtSecret, 900, 2592000, "taut-test", "taut-test-api"),
    redis = null,
    sms = null
)

fun generateToken(userId: String = "user-test", role: String = "CUSTOMER"): String = JWT.create()
    .withIssuer("taut-test")
    .withAudience("taut-test-api")
    .withSubject(userId)
    .withClaim("role", role)
    .withClaim("phone", "08123456789")
    .withJWTId(UUID.randomUUID().toString())
    .withNotBefore(Date(System.currentTimeMillis() - 60000))
    .withIssuedAt(Date())
    .withExpiresAt(Date(System.currentTimeMillis() + 3600000))
    .sign(Algorithm.HMAC256(jwtSecret))
```

### 1.4 Test Application Setup Pattern

```kotlin
testApplication {
    application {
        configureSerialization()
        configureSecurity(testConfig)
        configureStatusPages()
        configureRouting()
    }
    val r = client.get("/v1/categories") {
        header(HttpHeaders.Authorization, "Bearer ${generateToken()}")
    }
    assertEquals(HttpStatusCode.OK, r.status)
}
```

---

## 2. API Endpoint Integration Tests

### 2.1 Auth Routes

**File:** `backend/src/test/kotlin/com/taut/routes/AuthRoutesIntegrationTest.kt`  
**Routes:** `/v1/auth/otp/request`, `/v1/auth/otp/verify`, `/v1/auth/token/refresh`, `/v1/auth/pin/change`, `/v1/auth/pin/verify`

#### OTP Request Tests

| # | Test Case | Method | Input | Expected Status | Auth Required |
|---|-----------|--------|-------|-----------------|---------------|
| AR-01 | Valid OTP request | POST | `{"phone_number":"08123456789","device_id":"dev-1"}` | 200 OK | No |
| AR-02 | Missing phone number | POST | `{"device_id":"dev-1"}` | 400 Bad Request | No |
| AR-03 | Empty body | POST | `{}` | 400 Bad Request | No |
| AR-04 | Rate limited response | POST | Valid phone + mocked `RATE_LIMITED` | 429 Too Many Requests | No |
| AR-05 | With bank_sampah_id | POST | Include `bank_sampah_id` | 200 OK | No |
| AR-06 | Invalid phone format | POST | `{"phone_number":"invalid","device_id":"dev-1"}` | 400 Bad Request | No |
| AR-07 | Malformed JSON body | POST | `not-json` | 400 Bad Request | No |

#### OTP Verify Tests

| # | Test Case | Method | Input | Expected Status |
|---|-----------|--------|-------|-----------------|
| AV-01 | Valid OTP verify | POST | Valid session + "123456" + device_pub_key | 200 OK (access_token + refresh_token) |
| AV-02 | Expired OTP | POST | Valid session + mocked `OTP_EXPIRED` | 410 Gone |
| AV-03 | Already used OTP | POST | Valid session + mocked `OTP_ALREADY_USED` | 410 Gone |
| AV-04 | Invalid session ID | POST | `bad-session` + mocked `INVALID_SESSION` | 400 Bad Request |
| AV-05 | Empty session ID | POST | `{"session_id":""}` | 400 Bad Request |
| AV-06 | Invalid OTP code | POST | Mocked `INVALID_OTP` | 400 Bad Request |
| AV-07 | Malformed JSON | POST | `not-json` | 400 Bad Request |

#### Token Refresh Tests

| # | Test Case | Method | Input | Expected Status |
|---|-----------|--------|-------|-----------------|
| TR-01 | Refresh via header | POST | `X-Refresh-Token` header | 200 OK |
| TR-02 | Refresh via body | POST | `{"refresh_token":"rt-body","device_id":"dev-1"}` | 200 OK |
| TR-03 | Expired refresh token | POST | Mocked `TOKEN_EXPIRED` | 401 Unauthorized |
| TR-04 | Invalid refresh token | POST | Mocked `INVALID_TOKEN` | 400 Bad Request |

#### PIN Change Tests

| # | Test Case | Method | Input | Expected Status | Auth |
|---|-----------|--------|-------|-----------------|------|
| PC-01 | Valid PIN change | POST | user_id + old_pin + new_pin + idempotency_key | 200 OK | Required |
| PC-02 | Invalid old PIN | POST | Mocked `INVALID_PIN` | 400 Bad Request | Required |
| PC-03 | No auth token | POST | No Authorization header | Non-200 (401/403) | N/A |
| PC-04 | Missing idempotency key | POST | Valid without idempotency key | 200 OK | Required |

#### PIN Verify Tests

| # | Test Case | Method | Input | Expected Status | Auth |
|---|-----------|--------|-------|-----------------|------|
| PV-01 | Valid PIN | POST | user_id + pin (mocked `valid=true`) | 200 OK | Required |
| PV-02 | Rate limited | POST | Mocked rate limit hit | 429 Too Many Requests | Required |
| PV-03 | PIN locked | POST | Mocked `PIN_LOCKED` | 403 Forbidden | Required |
| PV-04 | Wrong PIN | POST | Mocked `INVALID_PIN` | 200 OK (valid=false) | Required |
| PV-05 | No auth token | POST | No Authorization header | Non-200 | N/A |
| PV-06 | Retry-After header | POST | Check response body has `retry_after` field | 429 | Required |

---

### 2.2 Transaction Routes

**File:** `backend/src/test/kotlin/com/taut/routes/TransactionRoutesTest.kt` (existing)  
**Routes:** `/v1/transactions`, `/v1/transactions/{id}`, `/v1/transactions/{id}/void`, `/v1/banks/{bank_id}/transactions`, `/v1/customers/{id}/transactions`

**Existing Test Coverage (12 tests):**

| # | Test Case | Expected Status |
|---|-----------|-----------------|
| TX-01 | POST with valid token returns 201 | 201 Created |
| TX-02 | POST without token returns 401 | 401 Unauthorized |
| TX-03 | POST with invalid token returns 401 | 401 Unauthorized |
| TX-04 | GET by id returns 200 when found | 200 OK |
| TX-05 | GET by id returns 404 when not found | 404 Not Found |
| TX-06 | GET bank transactions with filters | 200 OK |
| TX-07 | GET customer transactions | 200 OK |
| TX-08 | POST void without auth | 401 Unauthorized |
| TX-09 | POST duplicate (idempotency) returns 409 | 409 Conflict |

**Additional Tests Needed:**

| # | Test Case | Description |
|---|-----------|-------------|
| TX-10 | GET bank transactions without auth | 401 Unauthorized |
| TX-11 | GET customer transactions without auth | 401 Unauthorized |
| TX-12 | POST void with OPERATOR role | 200 OK |
| TX-13 | POST void already voided | 409 Conflict |
| TX-14 | POST void not found | 404 Not Found |
| TX-15 | POST transaction with invalid body | 400 Bad Request |
| TX-16 | GET pagination params respected | Verify page/page_size in response |

---

### 2.3 Catalog Routes

**File:** `backend/src/test/kotlin/com/taut/routes/CatalogRoutesTest.kt` (existing)  
**Routes:** `/v1/categories`, `/v1/prices`, `/v1/categories/{id}/prices/history`

**Existing Test Coverage (7 tests):**

| # | Test Case | Expected |
|---|-----------|----------|
| CT-01 | GET categories with auth | 200 OK |
| CT-02 | GET categories without auth (public) | 200 OK |
| CT-03 | GET prices with lastVersion param | 200 OK |
| CT-04 | GET prices without auth | 401 Unauthorized |
| CT-05 | GET price history with auth | 200 OK |
| CT-06 | GET price history without auth | 401 Unauthorized |

**Additional Tests Needed:**

| # | Test Case | Description |
|---|-----------|-------------|
| CT-07 | GET categories empty list | Return empty `data` array |
| CT-08 | GET prices with region_id | Valid 200 response |
| CT-09 | GET price history with date range | Valid 200 with filtered results |

---

### 2.4 Dashboard Routes

**File:** `backend/src/test/kotlin/com/taut/routes/DashboardRoutesIntegrationTest.kt`  
**Routes:** `/v1/dashboard/operator/{bank_id}`, `/v1/dashboard/dlh/{kota_id}`, `/v1/dashboard/operator/{bank_id}/report`

| # | Test Case | Method | Auth | Role | Expected |
|---|-----------|--------|------|------|----------|
| DB-01 | GET operator dashboard | GET | Required | OPERATOR | 200 OK |
| DB-02 | GET operator dashboard with custom period | GET | Required | OPERATOR | 200 OK |
| DB-03 | GET operator dashboard as CUSTOMER | GET | Required | CUSTOMER | 403 Forbidden |
| DB-04 | GET operator dashboard without auth | GET | No | — | 401 Unauthorized |
| DB-05 | GET operator dashboard service error | GET | Required | OPERATOR | 400 Bad Request |
| DB-06 | GET DLH dashboard | GET | Required | DLH_ADMIN | 200 OK |
| DB-07 | GET DLH dashboard as OPERATOR | GET | Required | OPERATOR | 403 Forbidden |
| DB-08 | GET DLH dashboard missing date params | GET | Required | DLH_ADMIN | 400 Bad Request |
| DB-09 | GET DLH dashboard invalid kota_id | GET | Required | DLH_ADMIN | 400 Bad Request |
| DB-10 | GET operator report | GET | Required | OPERATOR | 200 OK (report_url) |
| DB-11 | GET operator report default params | GET | Required | OPERATOR | 200 OK |

---

### 2.5 Device Routes

**File:** `backend/src/test/kotlin/com/taut/routes/DeviceRoutesIntegrationTest.kt`  
**Routes:** `/v1/devices/register`, `/v1/devices`, `/v1/devices/{id}/wipe`

| # | Test Case | Method | Auth | Expected |
|---|-----------|--------|------|----------|
| DV-01 | POST register device valid | POST | No | 201 Created |
| DV-02 | POST register device missing bank | POST | No | 400 Bad Request |
| DV-03 | GET list devices | GET | Required | 200 OK |
| DV-04 | GET list devices missing bank_id | GET | Required | 400 Bad Request |
| DV-05 | GET list devices without auth | GET | No | 401 Unauthorized |
| DV-06 | POST wipe device as OPERATOR | POST | Required | 200 OK |
| DV-07 | POST wipe device as CUSTOMER | POST | Required | 403 Forbidden |
| DV-08 | POST wipe device not found | POST | Required | 404 Not Found |
| DV-09 | POST wipe device without auth | POST | No | 401 Unauthorized |
| DV-10 | POST wipe device already wiped | POST | Required | 409 Conflict |

---

### 2.6 Compliance Routes

**File:** `backend/src/test/kotlin/com/taut/routes/ComplianceRoutesIntegrationTest.kt`  
**Routes:** `/v1/users/{id}/export`, `/v1/users/{id}/forget`, `/v1/consent` (GET + POST)

These routes have database dependencies (Jdbc.withConn). Tests must mock DB layer or use in-memory H2.

| # | Test Case | Method | Auth | Expected |
|---|-----------|--------|------|----------|
| CP-01 | GET user export with valid auth | GET | Required | 200 OK |
| CP-02 | GET user export without auth | GET | No | 401 Unauthorized |
| CP-03 | GET user export invalid UUID | GET | Required | 400 Bad Request |
| CP-04 | POST user forget | POST | Required | 200 OK |
| CP-05 | GET consent status | GET | Required | 200 OK |
| CP-06 | POST consent update | POST | Required | 200 OK |
| CP-07 | POST consent without auth | POST | No | 401 Unauthorized |
| CP-08 | POST consent missing consent_type | POST | Required | 400 Bad Request |

---

### 2.7 SMS Routes

**File:** `backend/src/test/kotlin/com/taut/routes/SmsRoutesIntegrationTest.kt`  
**Routes:** `/v1/sms/queue`, `/v1/sms/queue/{id}/retry`

| # | Test Case | Method | Auth | Role | Expected |
|---|-----------|--------|------|------|----------|
| SM-01 | GET SMS queue as DLH_ADMIN | GET | Required | DLH_ADMIN | 200 OK |
| SM-02 | GET SMS queue as OPERATOR | GET | Required | OPERATOR | 403 Forbidden |
| SM-03 | GET SMS queue without auth | GET | No | — | 401 Unauthorized |
| SM-04 | POST SMS retry as DLH_ADMIN | POST | Required | DLH_ADMIN | 200 OK |
| SM-05 | POST SMS retry not found | POST | Required | DLH_ADMIN | 404 Not Found |
| SM-06 | POST SMS retry max retries | POST | Required | DLH_ADMIN | 410 Gone |

---

### 2.8 Sync Routes

**File:** `backend/src/test/kotlin/com/taut/routes/SyncRoutesIntegrationTest.kt`  
**Routes:** `/v1/sync/status`, `/v1/sync/push`

> **Note:** These endpoints are REST stubs for development. Real sync uses gRPC (SyncService). All return 501 NotImplemented currently.

| # | Test Case | Method | Auth | Expected |
|---|-----------|--------|------|----------|
| SY-01 | GET sync status | GET | Required | 501 Not Implemented |
| SY-02 | GET sync status without auth | GET | No | 401 Unauthorized |
| SY-03 | GET sync status missing device_id | GET | Required | 501 Not Implemented |
| SY-04 | POST sync push | POST | Required | 501 Not Implemented |
| SY-05 | POST sync push without auth | POST | No | 401 Unauthorized |
| SY-06 | POST sync push with device headers | POST | Required | 501 Not Implemented |

---

### 2.9 Health Endpoint

**File:** Covered in `SecurityTest.kt`  
**Route:** `/health`

| # | Test Case | Expected |
|---|-----------|----------|
| HE-01 | GET health without auth | 200 OK or 503 (no DB in test) |
| HE-02 | GET health returns valid JSON | Response parses as JSON |

---

## 3. Sync Flow Test Plan (gRPC)

### 3.1 Bidirectional Stream Flow

The gRPC `SyncService.Sync` (bidi stream) is the primary sync channel. Defined in `proto/taut/core/v1/sync_service.proto`.

**Stream Contract:**

```
1. Client opens stream with SyncBatch (metadata only, empty transactions)
2. Server responds with SyncResponse (catalog updates, server time)
3. Client sends one or more SyncBatch messages with transactions
4. Server acks each batch with SyncResponse
5. Server may push catalog/price updates anytime via SyncResponse
6. Client closes stream when done
7. Server closes stream
```

| # | Test Case | Description | Verification |
|---|-----------|-------------|--------------|
| GS-01 | **Happy path — single batch** | Client sends 1 SyncBatch with 5 transactions; server acks all | All returned AckEntry have `status=SYNCED` |
| GS-02 | **Happy path — multiple batches** | Client sends 3 sequential batches (5, 10, 3 transactions) | Each batch gets a SyncResponse; total acks = 18 |
| GS-03 | **Empty batch** | Client sends SyncBatch with `transactions=[]` | Server returns SyncResponse with empty `acks` |
| GS-04 | **Server push catalog update** | During stream idle, server sends price change | Client receives catalog_updates mid-stream |
| GS-05 | **Stream reconnect** | Client disconnects mid-stream, reconnects with same cursor | Server resumes from last processed transaction |
| GS-06 | **High throughput** | Client sends 1000 transactions in one batch | All 1000 acked within acceptable time |
| GS-07 | **Stream timeout** | Client opens stream but sends nothing for 60s | Server closes stream with DEADLINE_EXCEEDED |
| GS-08 | **Invalid transaction** | Batch contains transaction with negative weight | Server returns AckEntry with `status=FAILED` + error message |

### 3.2 Unary Sync Flow

| # | Test Case | Description | Expected |
|---|-----------|-------------|----------|
| GU-01 | **SyncUnary happy path** | Single `SyncBatch` via unary RPC | `SyncResponse` with acks |
| GU-02 | **SyncUnary large payload** | 100 transactions in one request | Response within 5s |
| GU-03 | **SyncUnary invalid auth** | Missing/invalid JWT in metadata | gRPC UNAUTHENTICATED status |

### 3.3 Conflict Resolution

| # | Test Case | Description | Expected |
|---|-----------|-------------|----------|
| GC-01 | **Lamport timestamp conflict** | Two transactions with same Lamport timestamp | Server resolves by transaction UUID ordering |
| GC-02 | **Duplicate sync** | Same SyncBatch sent twice (idempotent) | Server detects `processed_ids` and returns existing acks |
| GC-03 | **Stale cursor** | Client sends batch with old cursor | Server rejects and returns current cursor |
| GC-04 | **Counter delta conflict** | Two devices update same counter concurrently | Last-write-wins based on Lamport timestamp |

### 3.4 Offline Queue Tests

| # | Test Case | Description | Verification |
|---|-----------|-------------|--------------|
| GO-01 | **Queue accumulation** | 100 transactions queued offline | All sync successfully on reconnect |
| GO-02 | **Partial sync failure** | First 5 of 10 transactions fail | Remaining 5 proceed; failed ones returned with proper error |
| GO-03 | **Sync during poor connectivity** | Network drops mid-stream | Client retries with exponential backoff; no duplicate transactions |
| GO-04 | **Max queue size** | Queue exceeds 5000 pending transactions | Oldest transactions are prioritized; new ones warned |

---

## 4. Auth Flow Test Plan

### 4.1 JWT Token Lifecycle

**Test File:** `backend/src/test/kotlin/com/taut/service/AuthServiceTest.kt` (existing)  
**Config:** `JwtConfig(secret, accessExpiry=900s, refreshExpiry=2592000s)`

#### Token Generation Tests

| # | Test Case | Description | Expected |
|---|-----------|-------------|----------|
| JT-01 | **Generate access token** | `generateAccessToken("user-123", "CUSTOMER", "08123456789")` | Valid JWT string with 3 dot-separated segments |
| JT-02 | **Correct subject** | Token for user-42 | Decoded subject = "user-42" |
| JT-03 | **Correct role claim** | Token for role ADMIN | Decoded `role` claim = "ADMIN" |
| JT-04 | **Correct phone claim** | Token for phone 08987654321 | Decoded `phone` claim = "08987654321" |
| JT-05 | **JTI (JWT ID) present** | Every token gets unique jti | Decoded `jti` is non-null UUID |
| JT-06 | **Issued at (iat) set** | Token creation time | `iat` is approximately `now` (±1s) |
| JT-07 | **Not before (nbf) set** | Token valid-from time | `nbf` is approximately `now` |
| JT-08 | **Expiration (exp) set** | Token future expiry | `exp` = `iat` + `accessTokenExpirySeconds` |
| JT-09 | **Different users different tokens** | user-A vs user-B | Different subjects, different jtis |

#### Token Validation Tests

| # | Test Case | Description | Expected |
|---|-----------|-------------|----------|
| JV-01 | **Valid token accepted** | Token signed with correct secret | Verification succeeds |
| JV-02 | **Wrong secret rejected** | Token signed with different secret | `JWTVerificationException` |
| JV-03 | **Wrong issuer rejected** | Token claims wrong issuer | `JWTVerificationException` |
| JV-04 | **Wrong audience rejected** | Token claims wrong audience | `JWTVerificationException` |
| JV-05 | **Expired token rejected** | Token with past `exp` | `TokenExpiredException` |
| JV-06 | **Token without subject rejected** | No `sub` claim | Application rejects (sub = null) |
| JV-07 | **Tampered token rejected** | Modified payload (alg=none attack) | Ktor security plugin rejects |
| JV-08 | **Leeway grace period** | Token expired < 60s ago | Accepted (acceptLeeway=60) |

#### Token Refresh Lifecycle

| # | Test Case | Description | Expected |
|---|-----------|-------------|----------|
| JR-01 | **Refresh rotation** | Refresh token used → new token issued, old revoked | Old token invalid after use |
| JR-02 | **Refresh expiry** | Refresh token older than 30 days | Returns `TOKEN_EXPIRED` |
| JR-03 | **Refresh token replay** | Use revoked refresh token | Returns `INVALID_TOKEN` |
| JR-04 | **Concurrent refresh** | Two devices try refresh same token | Only first succeeds; second gets `INVALID_TOKEN` |

---

### 4.2 PIN Verification Flow

| # | Test Case | Description | Expected |
|---|-----------|-------------|----------|
| PI-01 | **First-time PIN setup** | `changePin(userId, "", "1234", "")` (no old PIN) | PIN saved, success returned |
| PI-02 | **PIN change with valid old PIN** | `changePin(userId, "1234", "5678", "key")` | Success, new PIN verifiable |
| PI-03 | **PIN change wrong old PIN** | `changePin(userId, "wrong", "5678", "key")` | `INVALID_PIN` error |
| PI-04 | **PIN verify correct** | `verifyPin(userId, "1234")` | `valid=true`, `remaining_attempts=10` |
| PI-05 | **PIN verify wrong** | `verifyPin(userId, "wrong")` | `valid=false`, `remaining_attempts=9` |
| PI-06 | **PIN lock after 10 failures** | 10 consecutive wrong attempts | `PIN_LOCKED`, account blocked |
| PI-07 | **PIN verify after lockout** | Attempt after lock | `PIN_LOCKED`, no PIN check |
| PI-08 | **PIN not set** | User with no PIN hash | `PIN_NOT_SET` error |
| PI-09 | **Idempotent PIN change** | Same idempotency_key used twice | Second call returns success (no-op) |
| PI-10 | **PIN rate limiting** | IP-based rate limit on `/pin/verify` | After N attempts in window → 429 |

---

### 4.3 OTP Flow Tests

| # | Test Case | Description | Expected |
|---|-----------|-------------|----------|
| OT-01 | **OTP request** | Valid phone number | session_id + expires_seconds + masked_phone |
| OT-02 | **OTP request rate limit (phone)** | 4th request in 60s window | `RATE_LIMITED` error |
| OT-03 | **OTP request rate limit (device)** | 6th request in 10-min window | `RATE_LIMITED` error |
| OT-04 | **OTP verify correct** | OTP matches hash | access_token + refresh_token |
| OT-05 | **OTP verify expired** | After 300s | `OTP_EXPIRED` |
| OT-06 | **OTP verify already used** | Second verify with same session | `OTP_ALREADY_USED` |
| OT-07 | **OTP verify wrong code** | Mismatch | `INVALID_OTP` |
| OT-08 | **OTP verify new user** | Phone not in users table | New user created, `is_new_user=true` |
| OT-09 | **OTP verify existing user** | Returning user | `is_new_user=false` |
| OT-10 | **OTP verify rate limit** | 6th verify attempt in 5-min window | `RATE_LIMITED` |

---

## 5. Security Test Plan

### 5.1 Rate Limiting

**Implemented in:** `AuthService` (phone-based, device-based, IP-based PIN verify)

| # | Test Case | Endpoint | Limit | Expected |
|---|-----------|----------|-------|----------|
| RL-01 | OTP request phone limit | POST `/v1/auth/otp/request` | 3 per 60s per phone | 4th request → 429 |
| RL-02 | OTP request device limit | POST `/v1/auth/otp/request` | 5 per 600s per device | 6th request → 429 |
| RL-03 | OTP verify rate limit | POST `/v1/auth/otp/verify` | 5 per 300s per phone | 6th attempt → 429 |
| RL-04 | PIN verify IP rate limit | POST `/v1/auth/pin/verify` | Configurable (default ~10/5min) | After threshold → 429 + `retry_after` |
| RL-05 | Rate limit resets after window | POST `/v1/auth/otp/request` | After 60s cooldown | Request succeeds |
| RL-06 | Distinct OTP req vs verify buckets | Separate counters | Different prefixes (`v:` for verify) | OTP verify doesn't affect OTP request limit |
| RL-07 | Stale rate limit row cleanup | DB `rate_limits` table | Auto-delete after 2× window | Table doesn't grow unbounded |

**Test Strategy for Rate Limiting:**
- Use mock for `checkPhoneRateLimit()` / `checkDeviceRateLimit()` to simulate near-limit states
- For IP-based PIN verify: mock `checkPinVerifyRateLimit()` to return `allowed=false` + `retryAfter`

### 5.2 CORS Configuration

| # | Test Case | Request Origin | Expected |
|---|-----------|----------------|----------|
| CR-01 | Allowed origin (api.taut.id) | `Origin: https://api.taut.id` | `Access-Control-Allow-Origin` present |
| CR-02 | Allowed origin (dashboard) | `Origin: https://dashboard.taut.id` | CORS headers present |
| CR-03 | Disallowed origin | `Origin: https://evil.com` | No CORS headers (or rejected) |
| CR-04 | Preflight OPTIONS | `OPTIONS` with `Access-Control-Request-Method: POST` | 200 OK with proper CORS headers |
| CR-05 | Allowed methods | GET, POST, PUT, DELETE, PATCH | CORS allows these methods |
| CR-06 | Credentials | With credentials flag | `Access-Control-Allow-Credentials: true` |

### 5.3 Input Validation

| # | Test Case | Endpoint | Malformed Input | Expected |
|---|-----------|----------|----------------|----------|
| IV-01 | Invalid JSON body | Any POST | `not-json` | 400 Bad Request |
| IV-02 | Empty request body | POST transactions | `{}` | 400 or service-appropriate |
| IV-03 | SQL injection attempt | GET `/v1/transactions` | `?status=1' OR '1'='1` | No SQL error leakage; returns empty or 400 |
| IV-04 | XSS in parameter | GET `/v1/categories` | `?name=<script>alert(1)</script>` | Script encoded in response |
| IV-05 | UUID injection | GET `/v1/transactions/{id}` | `not-a-uuid` | 400 Bad Request |
| IV-06 | Negative weight | POST transaction | `{"weight": -100}` | 400 Bad Request |
| IV-07 | Oversized payload | POST sync push | 10MB JSON body | 413 Payload Too Large |
| IV-08 | Path traversal | Any path | `../../etc/passwd` | 404 or safe response |
| IV-09 | Missing required field | POST OTP | No `phone_number` | 400 Bad Request |
| IV-10 | Type mismatch | POST | String instead of number | 400 Bad Request |

### 5.4 Authentication & Authorization

**File:** `backend/src/test/kotlin/com/taut/routes/SecurityTest.kt` (existing)

| # | Test Case | Route | Expected |
|---|-----------|-------|----------|
| SA-01 | Health endpoint public | GET `/health` | OK without auth |
| SA-02 | Categories endpoint public | GET `/v1/categories` | 200 without auth |
| SA-03 | Auth endpoints accessible | POST `/v1/auth/otp/request` | No auth required |
| SA-04 | Protected (no token) | POST `/v1/transactions` | 401 Unauthorized |
| SA-05 | Protected (invalid token) | POST `/v1/transactions` | 401 Unauthorized |
| SA-06 | Protected (expired token) | POST `/v1/transactions` | 401 Unauthorized |
| SA-07 | RBAC OPERATOR allowed | POST `/v1/devices/wipe` | 200 (OPERATOR) |
| SA-08 | RBAC CUSTOMER denied | POST `/v1/devices/wipe` | 403 (CUSTOMER) |
| SA-09 | RBAC DLH_ADMIN allowed | GET `/v1/dashboard/dlh/1` | 200 (DLH_ADMIN) |
| SA-10 | RBAC OPERATOR denied | GET `/v1/dashboard/dlh/1` | 403 (OPERATOR) |
| SA-11 | PIN change requires auth | POST `/v1/auth/pin/change` | Non-200 without auth |
| SA-12 | PIN verify requires auth | POST `/v1/auth/pin/verify` | Non-200 without auth |
| SA-13 | Device register public | POST `/v1/devices/register` | Accessible without auth |
| SA-14 | Device list requires auth | GET `/v1/devices` | 401 without auth |

### 5.5 Security Headers

| # | Test Case | Header | Expected |
|---|-----------|--------|----------|
| SH-01 | Strict-Transport-Security | Response header present | `max-age=31536000; includeSubDomains` |
| SH-02 | X-Content-Type-Options | Response header present | `nosniff` |
| SH-03 | X-Frame-Options | Response header present | `DENY` (or `SAMEORIGIN`) |
| SH-04 | Content-Security-Policy | Response header present | Reasonable CSP value |

---

## 6. Test Execution Guide

### 6.1 Running Tests

**Prerequisites:**
- Java 21 (JDK from Android Studio: `"C:/Program Files/Android/Android Studio/jbr"`)
- Gradle wrapper (bundled in backend/)

**Command:**
```bash
cd /c/Users/AcerAG14/Documents/Project/Hermes/#1/backend
export JAVA_HOME="/c/Program Files/Android/Android Studio/jbr"
./gradlew test --no-daemon
```

**Run specific test class:**
```bash
./gradlew test --tests "com.taut.routes.AuthRoutesIntegrationTest" --no-daemon
```

**Run specific test method:**
```bash
./gradlew test --tests "com.taut.routes.AuthRoutesIntegrationTest.POST otp request returns 200 with valid phone" --no-daemon
```

### 6.2 Test File Structure

```
backend/src/test/kotlin/com/taut/
├── routes/
│   ├── AuthRoutesIntegrationTest.kt        # NEW: 33 auth tests
│   ├── CatalogRoutesTest.kt                # EXISTING: 6 catalog tests
│   ├── DashboardRoutesIntegrationTest.kt   # NEW: 11 dashboard tests
│   ├── DeviceRoutesIntegrationTest.kt      # NEW: 10 device tests
│   ├── SecurityTest.kt                     # EXISTING: 14 security tests
│   ├── SyncRoutesIntegrationTest.kt        # NEW: 6 sync tests
│   └── TransactionRoutesTest.kt            # EXISTING: 9 transaction tests
├── service/
│   ├── AuthServiceTest.kt                  # EXISTING: 11 JWT unit tests
│   └── TransactionServiceTest.kt           # EXISTING: 10 unit tests
```

### 6.3 Test Count Summary

| Category | Existing | New | Total |
|----------|----------|-----|-------|
| Auth routes | 0 | 33 | 33 |
| Transaction routes | 9 | 7 (planned) | 16 |
| Catalog routes | 6 | 3 (planned) | 9 |
| Dashboard routes | 0 | 11 | 11 |
| Device routes | 0 | 10 | 10 |
| Compliance routes | 0 | 8 (planned) | 8 |
| SMS routes | 0 | 6 (planned) | 6 |
| Sync routes | 0 | 6 | 6 |
| Health | 1 | 1 (planned) | 2 |
| **Route integration subtotal** | **16** | **85** | **101** |
| JWT unit tests (AuthService) | 11 | 0 | 11 |
| Transaction service unit tests | 10 | 0 | 10 |
| **Grand total** | **37** | **85** | **122** |

### 6.4 CI Integration Snippet

```yaml
# .github/workflows/test.yml (suggested)
name: Backend Tests
on: [push, pull_request]
jobs:
  test:
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v4
      - uses: actions/setup-java@v4
        with:
          java-version: '21'
          distribution: 'temurin'
      - name: Run tests
        run: |
          cd backend
          ./gradlew test --no-daemon
      - name: Publish test report
        uses: dorny/test-reporter@v1
        if: success() || failure()
        with:
          name: Test Results
          path: backend/build/reports/tests/test/
          reporter: java-junit
```

---

## 7. Test Coverage Matrix

### 7.1 Route Coverage

| Route | Method | Auth Required | Tested | Test File |
|-------|--------|-------------|--------|-----------|
| `/health` | GET | No | ✅ | SecurityTest |
| `/v1/auth/otp/request` | POST | No | ✅ | AuthRoutesIntegrationTest |
| `/v1/auth/otp/verify` | POST | No | ✅ | AuthRoutesIntegrationTest |
| `/v1/auth/token/refresh` | POST | No | ✅ | AuthRoutesIntegrationTest |
| `/v1/auth/pin/change` | POST | Yes | ✅ | AuthRoutesIntegrationTest |
| `/v1/auth/pin/verify` | POST | Yes | ✅ | AuthRoutesIntegrationTest |
| `/v1/categories` | GET | No | ✅ | CatalogRoutesTest |
| `/v1/prices` | GET | Yes | ✅ | CatalogRoutesTest |
| `/v1/categories/{id}/prices/history` | GET | Yes | ✅ | CatalogRoutesTest |
| `/v1/transactions` | POST | Yes | ✅ | TransactionRoutesTest |
| `/v1/transactions/{id}` | GET | Yes | ✅ | TransactionRoutesTest |
| `/v1/transactions/{id}/void` | POST | Yes | ✅ | TransactionRoutesTest |
| `/v1/banks/{bank_id}/transactions` | GET | Yes | ✅ | TransactionRoutesTest |
| `/v1/customers/{id}/transactions` | GET | Yes | ✅ | TransactionRoutesTest |
| `/v1/dashboard/operator/{bank_id}` | GET | Yes | ✅ | DashboardRoutesIntegrationTest |
| `/v1/dashboard/dlh/{kota_id}` | GET | Yes | ✅ | DashboardRoutesIntegrationTest |
| `/v1/dashboard/operator/{bank_id}/report` | GET | Yes | ✅ | DashboardRoutesIntegrationTest |
| `/v1/devices/register` | POST | No | ✅ | DeviceRoutesIntegrationTest |
| `/v1/devices` | GET | Yes | ✅ | DeviceRoutesIntegrationTest |
| `/v1/devices/{id}/wipe` | POST | Yes | ✅ | DeviceRoutesIntegrationTest |
| `/v1/users/{id}/export` | GET | Yes | ⬜ | Planned |
| `/v1/users/{id}/forget` | POST | Yes | ⬜ | Planned |
| `/v1/consent` | GET | Yes | ⬜ | Planned |
| `/v1/consent` | POST | Yes | ⬜ | Planned |
| `/v1/sms/queue` | GET | Yes | ⬜ | Planned |
| `/v1/sms/queue/{id}/retry` | POST | Yes | ⬜ | Planned |
| `/v1/sync/status` | GET | Yes | ✅ | SyncRoutesIntegrationTest |
| `/v1/sync/push` | POST | Yes | ✅ | SyncRoutesIntegrationTest |

### 7.2 Security Coverage

| Category | Coverage Points | Status |
|----------|----------------|--------|
| JWT token generation | 9 tests | ✅ Covered |
| JWT token validation | 8 tests | ✅ Covered |
| Token refresh lifecycle | 4 tests | ✅ Covered |
| PIN setup/change | 9 tests | ✅ Covered |
| OTP flow | 10 tests | ✅ Covered |
| Rate limiting | 7 tests | ✅ Covered |
| CORS configuration | 6 tests | ⬜ Planned |
| Input validation | 10 tests | ⬜ Planned |
| Auth enforcement (public/private) | 14 tests | ✅ Covered |
| RBAC role checks | 6 tests | ✅ Covered |
| Security headers | 4 tests | ⬜ Planned |
| **Security total** | **87 tests** | **68 covered, 19 planned** |

### 7.3 gRPC Sync Coverage

| Category | Coverage Points | Status |
|----------|----------------|--------|
| Bidirectional stream flow | 8 tests | ⬜ Planned |
| Unary sync flow | 3 tests | ⬜ Planned |
| Conflict resolution | 4 tests | ⬜ Planned |
| Offline queue | 4 tests | ⬜ Planned |
| **gRPC sync total** | **19 tests** | **Planned** |

---

## Appendix A: Mocking Strategy

### A.1 Service Mock Pattern

```kotlin
@BeforeEach
fun setup() {
    mockkObject(AuthService)  // Replaces all AuthService methods with mocks
}

@AfterEach
fun teardown() {
    unmockkAll()  // Clean up after each test
}

@Test
fun `test case`() {
    every { AuthService.requestOtp("08123456789", "dev-1", any()) } returns mapOf(
        "session_id" to UUID.randomUUID().toString(),
        "expires_seconds" to 300,
        "masked_phone" to "0812****789"
    )
    // ... testApplication {} block ...
}
```

### A.2 Mocking DB-Dependent Routes

For routes that call `Jdbc.withConn {}` directly (ComplianceRoutes), use one of:
1. **Mock `Jdbc` object** with `mockkObject(Jdbc)` — mock `withConn` and `withTransaction`
2. **In-memory H2** — set `DatabaseConfig(url = "jdbc:h2:mem:test;DB_CLOSE_DELAY=-1")` and run Flyway migrations in `@BeforeAll`
3. **Wrap in service layer** — refactor into `ComplianceService` and mock that instead

---

## Appendix B: Test Data Templates

### B.1 Standard JWT Generator

```kotlin
private fun generateToken(userId: String = "user-test", role: String = "CUSTOMER"): String {
    return JWT.create()
        .withIssuer(jwtIssuer)
        .withAudience(jwtAudience)
        .withSubject(userId)
        .withClaim("role", role)
        .withClaim("phone", "08123456789")
        .withJWTId(UUID.randomUUID().toString())
        .withNotBefore(Date(System.currentTimeMillis() - 60000))
        .withIssuedAt(Date())
        .withExpiresAt(Date(System.currentTimeMillis() + 3600000))
        .sign(Algorithm.HMAC256(jwtSecret))
}
```

### B.2 Common AppConfig for Tests

```kotlin
private val testConfig = AppConfig(
    server = ServerConfig("0.0.0.0", 8080),
    database = DatabaseConfig("test-url", "test", "test", 5, 1, 300000, 30000, 1800000),
    jwt = JwtConfig(jwtSecret, 900, 2592000, jwtIssuer, jwtAudience),
    redis = null,
    sms = null
)
```

---

## Appendix C: Glossary

| Term | Definition |
|------|------------|
| **Ktor Test Host** | `testApplication {}` — in-process HTTP server for integration testing without binding to real ports |
| **MockK** | Kotlin mocking library used to replace service objects for isolated testing |
| **JWT** | JSON Web Token — used for stateless authentication |
| **gRPC** | Google's RPC framework used for bidirectional sync |
| **RBAC** | Role-Based Access Control — enforced by `authorizeRole()` |
| **Lamport Timestamp** | Logical clock used for conflict-free replicated data types (CRDT) in sync |
| **SyncBatch** | Protobuf message wrapping a batch of transactions for gRPC sync |
| **AckEntry** | Per-transaction acknowledgment with status (SYNCED/FAILED) |

---

*End of Test Plan Document*
