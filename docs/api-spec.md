# TAUT — API Contract Specification
## gRPC + REST API Design
**Version:** 2.1
**Date:** 24 Juni 2026

---

## Protocol Buffer Source Files

The authoritative `.proto` definitions live in `proto/taut/core/v1/` at the repository root:

| File | Contents |
|------|----------|
| `common.proto` | Shared types: `Uuid`, `Timestamp`, `Money`, `Weight`, `Pagination`, `SyncStatus`, `TransactionType`, `UserRole`, `Error` |
| `models.proto` | Core models: `WasteBank`, `User`, `OperatorProfile`, `Device`, `WasteCategory`, `Transaction`, `TransactionItem`, `PriceReference`, `SyncMetadata`, `SyncBatch`, `AckEntry`, `SyncResponse`, `SmsMessage`, `LedgerEntry`, `DailyRollup`, `WeeklyKecamatanRollup` |
| `auth_service.proto` | `AuthService`: `RequestOtp`, `VerifyOtp`, `RefreshToken`, `ChangePin`, `VerifyPin`, `RegisterDevice` |
| `transaction_service.proto` | `TransactionService`: `CreateTransaction`, `GetTransaction`, `ListTransactions`, `VoidTransaction`, `ListCustomerTransactions` |
| `sync_service.proto` | `SyncService`: `Sync` (bidi stream), `SyncUnary`, `GetSyncStatus`, `PushTransaction` |
| `catalog_service.proto` | `CatalogService`: `ListCategories`, `GetPriceCatalog`, `GetPriceHistory` |
| `dashboard_service.proto` | `DashboardService`: `GetOperatorDashboard`, `GetKecamatanDashboard`, `GenerateMonthlyReport` |

To compile:
```bash
protoc --proto_path=proto --grpc-java_out=build/generated proto/taut/core/v1/*.proto
```

---

## Table of Contents

1. [API Overview](#1-api-overview)
2. [Protocol Buffer Definitions](#2-protocol-buffer-definitions)
3. [gRPC Service Definitions](#3-grpc-service-definitions)
4. [REST Endpoints](#4-rest-endpoints)
5. [Request/Response Examples](#5-requestresponse-examples)
6. [Authentication Flow](#6-authentication-flow)
7. [Error Handling](#7-error-handling)
8. [Rate Limiting](#8-rate-limiting)
9. [API Versioning](#9-api-versioning)

---

## 1. API Overview

### 1.1 Protocol Selection

| Endpoint Type | Protocol | Use Case |
|--------------|----------|----------|
| **Mobile sync** | gRPC bidirectional stream | Sync engine: batch transactions, catalog updates, real-time push |
| **Mobile auth** | gRPC unary | OTP request, OTP verify, token refresh |
| **Web dashboard** | REST (JSON) | Operator dashboard, DLH dashboard, admin panel |
| **Public API** | REST (JSON) | SIPSN integration, data-as-service (Fase 3+) |
| **Webhook callbacks** | REST (POST) | SMS delivery receipts, payment callbacks (Fase 2+) |

### 1.2 Base URLs

```yaml
Production:
  gRPC:    sync.taut.id:443
  REST:    https://api.taut.id/v1
  CDN:     https://cdn.taut.id

Staging:
  gRPC:    sync.staging.taut.id:443
  REST:    https://api.staging.taut.id/v1
  CDN:     https://cdn.staging.taut.id

Development:
  gRPC:    sync.dev.taut.id:443
  REST:    https://api.dev.taut.id/v1
  CDN:     https://cdn.dev.taut.id
```

### 1.3 Common Headers

```yaml
# Required for all requests
Authorization: Bearer <jwt_access_token>

# Required for gRPC sync requests
X-Device-ID: <uuid of device>
X-Device-Signature: <base64 HMAC-SHA256 of request body>
X-Idempotency-Key: <uuidv7 client-generated>

# Optional
X-Request-ID: <uuid for distributed tracing>
Accept-Language: id-ID (default) | en-US
```

---

## 2. Protocol Buffer Definitions

The authoritative source for all protobuf definitions is now at `proto/taut/core/v1/` in the repository root. The definitions below summarize the key types and messages. For the full, compilable `.proto` files, see:

| File | Path |
|------|------|
| Common Types | `proto/taut/core/v1/common.proto` |
| Core Models | `proto/taut/core/v1/models.proto` |
| Auth Service | `proto/taut/core/v1/auth_service.proto` |
| Transaction Service | `proto/taut/core/v1/transaction_service.proto` |
| Sync Service | `proto/taut/core/v1/sync_service.proto` |
| Catalog Service | `proto/taut/core/v1/catalog_service.proto` |
| Dashboard Service | `proto/taut/core/v1/dashboard_service.proto` |

### 2.1 Common Types (`common.proto`)

| Message / Enum | Fields | Description |
|----------------|--------|-------------|
| `Uuid` | `string value` | UUIDv7 string wrapper |
| `Timestamp` | `int64 seconds, int32 nanos` | Unix timestamp (UTC) |
| `Money` | `int64 satuan, string currency` | Value in satuan rupiah (cents). Always integer. |
| `Weight` | `int64 grams` | Weight in grams. 5kg = 5000. |
| `Pagination` | `int32 page, int32 page_size, string cursor` | Page + cursor-based pagination |
| `PaginatedResponse` | `repeated string items, string next_cursor, int32 total_count` | Generic paginated wrapper |
| `Error` | `string code, string message, string detail, string request_id` | Standard error envelope |
| `SyncStatus` | `UNSPECIFIED, PENDING_SYNC, SYNCING, SYNCED, CONFIRMED, FAILED` | Offline-first sync state machine |
| `TransactionType` | `UNSPECIFIED, DEPOSIT, REDEMPTION` | Waste transaction type |
| `UserRole` | `UNSPECIFIED, OPERATOR, CUSTOMER, DLH_STAFF, ADMIN, SUPERADMIN` | User role enum |
| `Empty` | — | Empty message for parameterless RPCs |

### 2.2 Core Models (`models.proto`)

| Message | Key Fields | Description |
|---------|-----------|-------------|
| `WasteBank` | `Uuid id, string code, string name, string phone, int32 village_id` | Registered waste bank unit |
| `User` | `Uuid id, string phone_number, UserRole role, string name, string kyc_status` | System user (operator/customer/admin) |
| `OperatorProfile` | `Uuid id, Uuid bank_sampah_id, Uuid user_id, bool is_primary` | Links a user to a waste bank as operator |
| `Device` | `Uuid id, Uuid bank_sampah_id, string device_name, string device_pub_key` | Registered device with Ed25519 key |
| `WasteCategory` | `Uuid id, string code, string name_id, string category_group, int64 unit_price` | Waste category with current price |
| `Transaction` | `Uuid id, Uuid bank_sampah_id, Uuid customer_id, TransactionType, SyncStatus, Money total_value, repeated TransactionItem items` | Waste deposit/redemption transaction |
| `TransactionItem` | `Uuid id, Uuid category_id, int64 weight_grams, int64 price_per_unit, int64 total_value` | Line item within a transaction |
| `PriceReference` | `Uuid id, Uuid category_id, int32 region_id, int64 price_per_unit, Timestamp effective_from` | Historical/current price quote |
| `SyncMetadata` | `string device_id, Uuid bank_sampah_id, int64 lamport_timestamp, string last_sync_cursor` | Per-session sync metadata |
| `SyncBatch` | `SyncMetadata metadata, repeated Transaction transactions, map counter_deltas` | Batch of transactions pushed during sync |
| `AckEntry` | `Uuid local_id, Uuid server_id, SyncStatus status, string error_message` | Per-transaction sync acknowledgment |
| `SyncResponse` | `repeated AckEntry acks, repeated WasteCategory catalog_updates, int64 new_lamport_timestamp, string new_cursor` | Sync server response |
| `SmsMessage` | `Uuid id, string phone_to, string message, string status` | SMS message record |
| `LedgerEntry` | `Uuid id, Uuid account_id, string entry_type, int64 amount, int64 balance_after` | Double-entry ledger entry |
| `DailyRollup` | `string day, Uuid bank_sampah_id, int32 transaction_count, int64 total_weight_grams` | Daily aggregate statistics |
| `WeeklyKecamatanRollup` | `string week, int32 kecamatan_id, int64 total_weight_grams, int32 active_banks` | Weekly kecamatan-level aggregate |

## 3. gRPC Service Definitions

All service definitions are in real `.proto` files under `proto/taut/core/v1/`. Summary:

| Service | File | RPCs | Transport |
|---------|------|------|-----------|
| **SyncService** | `sync_service.proto` | `Sync` (bidi stream), `SyncUnary`, `GetSyncStatus`, `PushTransaction` | gRPC (bidi + unary) |
| **TransactionService** | `transaction_service.proto` | `CreateTransaction`, `GetTransaction`, `ListTransactions`, `VoidTransaction`, `ListCustomerTransactions` | gRPC unary |
| **AuthService** | `auth_service.proto` | `RequestOtp`, `VerifyOtp`, `RefreshToken`, `ChangePin`, `VerifyPin`, `RegisterDevice` | gRPC unary |
| **CatalogService** | `catalog_service.proto` | `ListCategories`, `GetPriceCatalog`, `GetPriceHistory` | gRPC unary |
| **DashboardService** | `dashboard_service.proto` | `GetOperatorDashboard`, `GetKecamatanDashboard`, `GenerateMonthlyReport` | gRPC unary |

### 3.1 Sync Stream Contract

```
1. Client opens stream with SyncBatch (metadata only, no transactions)
2. Server responds with SyncResponse (catalog updates, server time)
3. Client sends transactions in batches
4. Server acks each batch
5. Server may push catalog/price updates anytime
6. Client sends StreamClose when done
7. Server closes stream
```

See `proto/taut/core/v1/sync_service.proto` for the full request/response message definitions.

## 4. REST Endpoints

For web dashboard and public API consumers. Uses JSON over HTTPS.

### 4.1 Auth API

```yaml
# === Auth ===

POST /v1/auth/otp/request
  Description: Request SMS OTP for phone verification
  Auth: None (first request)
  Body:
    phone_number: "08123456789"
    device_id: "uuid"
  Response 200:
    session_id: "uuid"
    expires_seconds: 300
    masked_phone: "0812****789"
  Response 429:
    error: "RATE_LIMITED"
    message: "Terlalu banyak permintaan. Coba lagi dalam 60 detik."

POST /v1/auth/otp/verify
  Description: Verify OTP code
  Auth: None
  Body:
    session_id: "uuid"
    otp_code: "123456"
    device_id: "uuid"
    device_pub_key: "base64_ed25519_pubkey"
  Response 200:
    access_token: "jwt..."
    refresh_token: "opaque..."
    expires_in: 900
    user: { ... }
    is_new_user: false

POST /v1/auth/token/refresh
  Description: Refresh access token
  Auth: Refresh token (header: X-Refresh-Token)
  Body:
    device_id: "uuid"
    device_signature: "base64_hmac"
  Response 200:
    access_token: "jwt..."
    expires_in: 900

POST /v1/auth/pin/change
  Description: Change operator PIN
  Auth: Bearer token
  Body:
    old_pin: "1234"
    new_pin: "5678"
    idempotency_key: "uuidv7"
  Response 200:
    success: true

POST /v1/auth/pin/verify
  Description: Verify PIN server-side (device recovery)
  Auth: Bearer token
  Body:
    pin: "1234"
  Response 200:
    valid: true
    remaining_attempts: 9
```

### 4.2 Transaction API

```yaml
# === Transactions ===

GET /v1/banks/{bank_id}/transactions
  Description: List transactions for a bank sampah
  Auth: Bearer token (operator, admin)
  Query:
    from_date: ISO8601
    to_date: ISO8601
    type: deposit | redemption
    status: synced | confirmed | pending_sync | failed
    page: 1
    page_size: 20
  Response 200:
    data: [ Transaction, ... ]
    pagination: { next_cursor, total_count }

GET /v1/transactions/{id}
  Description: Get a single transaction with items
  Auth: Bearer token
  Response 200:
    transaction: { ... }
    items: [ TransactionItem, ... ]

POST /v1/transactions
  Description: Create a transaction (online path)
  Auth: Bearer token
  Headers:
    X-Idempotency-Key: "uuidv7"
  Body:
    bank_sampah_id: "uuid"
    operator_id: "uuid"
    customer_id: "uuid"
    items: [
      { category_id: "uuid", weight_grams: 5000 }
    ]
    device_timestamp: "2026-06-23T10:30:00Z"
  Response 201:
    transaction: { ... }
  Response 409:
    error: "DUPLICATE_ENTRY"
    existing_id: "uuid"

POST /v1/transactions/{id}/void
  Description: Void a transaction (admin only)
  Auth: Bearer token (admin)
  Body:
    reason: "Keyboard error — duplicate entry"
    idempotency_key: "uuidv7"
  Response 200:
    transaction: { ... (status: voided) }

GET /v1/customers/{id}/transactions
  Description: Customer transaction history
  Auth: Bearer token
  Query:
    page: 1
    page_size: 20
  Response 200:
    data: [ Transaction, ... ]
```

### 4.3 Sync API

```yaml
# === Sync ===

POST /v1/sync
  Description: One-shot sync (push pending transactions, pull updates)
  Auth: Bearer token
  Headers:
    X-Device-ID: "uuid"
    X-Device-Signature: "base64_hmac"
  Body:
    metadata:
      device_id: "uuid"
      bank_sampah_id: "uuid"
      lamport_timestamp: 42
      last_sync_cursor: "uuidv7"
      app_version: "0.1.0"
    transactions: [ Transaction, ... ]
    counter_deltas: {}
  Response 200:
    acks: [ { local_id, server_id, status, error_message } ]
    catalog_updates: [ WasteCategory, ... ]
    catalog_version: 43
    counter_updates: {}
    new_lamport_timestamp: 58
    new_cursor: "uuidv7"
    server_time: "2026-06-23T10:30:00Z"

GET /v1/sync/status
  Description: Get current sync status for device
  Auth: Bearer token
  Headers:
    X-Device-ID: "uuid"
  Response 200:
    last_lamport_timestamp: 42
    last_cursor: "uuidv7"
    last_sync_at: "2026-06-23T10:30:00Z"
    pending_count: 5
    failed_count: 0
```

### 4.4 Catalog API

```yaml
# === Catalog ===

GET /v1/categories
  Description: List all active waste categories
  Auth: Bearer token
  Response 200:
    data: [ WasteCategory, ... ]
    version: 42

GET /v1/prices
  Description: Get current price catalog
  Auth: Bearer token
  Query:
    region_id: integer (optional)
    last_version: integer (for delta sync)
  Response 200:
    data: [ PriceReference, ... ]
    version: 42

GET /v1/categories/{id}/prices/history
  Description: Price history for a category
  Auth: Bearer token
  Query:
    from_date: ISO8601
    to_date: ISO8601
  Response 200:
    data: [ PriceReference, ... ]
```

### 4.5 Device API

```yaml
# === Devices ===

POST /v1/devices/register
  Description: Register a new device
  Auth: Bearer token (admin)
  Body:
    bank_sampah_id: "uuid"
    device_name: "HP Bank Sampah Melati 1"
    device_phone_number: "08123456789"
    device_pub_key: "base64..."
  Response 201:
    device_id: "uuid"
    device_secret: "base64_encrypted..."

GET /v1/devices
  Description: List registered devices for a bank
  Auth: Bearer token
  Query:
    bank_sampah_id: "uuid"
  Response 200:
    data: [ Device, ... ]

POST /v1/devices/{id}/wipe
  Description: Trigger remote wipe of device data
  Auth: Bearer token (admin + WhatsApp verification)
  Response 200:
    message: "Remote wipe initiated"
```

### 4.6 Dashboard

```yaml
# === Dashboard ===

GET /v1/dashboard/operator/{bank_id}
  Description: Operator dashboard data
  Auth: Bearer token (operator or admin of this bank)
  Query:
    period: today | this_week | this_month | custom
    from_date: ISO8601 (required if period=custom)
    to_date: ISO8601 (required if period=custom)
  Response 200:
    total_weight_grams: 500000
    total_value_satuan: 750000
    transaction_count: 42
    unique_customers: 18
    category_breakdown: [ ... ]
    daily_trend: [ ... ]

GET /v1/dashboard/dlh/{kota_id}
  Description: DLH dashboard data per kota
  Auth: Bearer token (dlh_staff, scoped to this kota)
  Query:
    from_date: ISO8601
    to_date: ISO8601
  Response 200:
    kecamatans: [ ... ]
    totals: { ... }

GET /v1/dashboard/operator/{bank_id}/report
  Description: Generate monthly report PDF
  Auth: Bearer token
  Query:
    year: 2026
    month: 6
  Response 200:
    report_url: "https://cdn.taut.id/reports/bsa-0001/2026/06/report.pdf"
    generated_at: "2026-06-23T12:00:00Z"
```

### 4.7 Compliance (UU PDP)

```yaml
# === Compliance (UU PDP) ===

GET /v1/users/{id}/export
  Description: Export all PII for a user (Pasal 26)
  Auth: Bearer token (user themselves or admin)
  Response 200:
    user: { ... }
    consents: [ ... ]
    transactions: [ ... ]
    device_history: [ ... ]

POST /v1/users/{id}/forget
  Description: Anonymize user data (right to be forgotten)
  Auth: Bearer token (user or admin)
  Headers:
    X-Admin-Code: "admin_confirmation_code"  # Required if requested by admin, not user
  Response 200:
    message: "Data akan dianonimkan dalam 72 jam"
    request_id: "uuid"

GET /v1/consent
  Description: Get current consent status for the authenticated user
  Auth: Bearer token
  Response 200:
    transaction_recording: { granted: true, version: "1.0", timestamp: "..." }
    dlh_data_sharing: { granted: false, ... }

POST /v1/consent
  Description: Update consent preference
  Auth: Bearer token
  Body:
    consent_type: "dlh_data_sharing"
    granted: true
    consent_version: "1.0"
  Response 200:
    success: true
```

### 4.8 SMS (Admin)

```yaml
# === SMS (Admin) ===

GET /v1/sms/queue
  Description: List pending/failed SMS messages
  Auth: Bearer token (admin)
  Query:
    status: pending | sent | failed | bounced
    bank_sampah_id: "uuid"
    page: 1
    page_size: 50
  Response 200:
    data: [ SmsMessage, ... ]
    pagination: { ... }

POST /v1/sms/resend
  Description: Manually resend a failed SMS
  Auth: Bearer token (admin)
  Body:
    sms_id: "uuid"
  Response 200:
    success: true
```

### 4.9 Webhook Callbacks

```yaml
# === Webhooks ===

POST /v1/webhooks/sms/delivery
  Description: SMS delivery receipt from provider
  Auth: HMAC signed (shared secret)
  Body:
    message_id: "provider_msg_id"
    status: "delivered" | "failed" | "bounced"
    timestamp: "2026-06-23T10:30:00Z"
    error_code: "..." (if failed)
  Response 200:
    received: true

POST /v1/webhooks/payment/complete      # Fase 2+
  Description: QRIS payment complete from PSP
  Auth: HMAC signed (PSP shared secret)
  Body:
    transaction_id: "psp_tx_id"
    reference_id: "taut_tx_id"
    amount: 75000
    status: "success"
    timestamp: "..."
  Response 200:
    received: true
```

---

## 5. Request/Response Examples

### 5.1 gRPC Bidirectional Sync

**Client → Server (first message — metadata only)**

```json
// Protobuf serialized, shown as JSON for readability
{
  "metadata": {
    "device_id": "018e0000-0000-7000-b000-000000000001",
    "bank_sampah_id": "018e0000-0000-7000-b000-000000000002",
    "lamport_timestamp": 42,
    "last_sync_cursor": "018e0000-0000-7000-b000-000000000099",
    "app_version": "0.1.0"
  },
  "transactions": [],
  "counter_deltas": {}
}
```

**Server → Client (response with catalog updates)**

```json
{
  "acks": [],
  "catalog_updates": [
    {
      "id": { "value": "018e0000-0000-7000-b000-000000000100" },
      "code": "KRD-01",
      "name_id": "Kardus",
      "category_group": "kardus",
      "unit_price": 2500,
      "is_active": true
    }
  ],
  "catalog_version": 43,
  "counter_updates": {},
  "new_lamport_timestamp": 58,
  "new_cursor": "018e0000-0000-7000-b000-000000000100",
  "server_time": { "seconds": 1800000000, "nanos": 0 }
}
```

**Client → Server (batch of offline transactions)**

```json
{
  "metadata": {
    "device_id": "018e0000-0000-7000-b000-000000000001",
    "bank_sampah_id": "018e0000-0000-7000-b000-000000000002",
    "lamport_timestamp": 58,
    "last_sync_cursor": "018e0000-0000-7000-b000-000000000100"
  },
  "transactions": [
    {
      "id": { "value": "018e0000-0000-7000-b000-000000000101" },
      "bank_sampah_id": { "value": "018e0000-0000-7000-b000-000000000002" },
      "operator_id": { "value": "018e0000-0000-7000-b000-000000000003" },
      "customer_id": { "value": "018e0000-0000-7000-b000-000000000004" },
      "transaction_type": "DEPOSIT",
      "total_weight_grams": 5000,
      "total_value": { "satuan": 7500, "currency": "IDR" },
      "device_timestamp": { "seconds": 1799990000, "nanos": 0 },
      "is_offline_created": true,
      "lamport_timestamp": 43,
      "hmac_signature": "MEUCIQD33...",
      "price_snapshot": "{\"KRD-01\":1500,\"PLT-01\":2000}",
      "items": [
        {
          "category_id": { "value": "018e0000-0000-7000-b000-000000000100" },
          "weight_grams": 5000,
          "price_per_unit": 1500,
          "total_value": 7500
        }
      ]
    }
  ]
}
```

**Server → Client (acknowledgments)**

```json
{
  "acks": [
    {
      "local_id": { "value": "018e0000-0000-7000-b000-000000000101" },
      "server_id": { "value": "018e0000-0000-7000-b000-000000000200" },
      "status": "SYNCED",
      "error_message": ""
    }
  ],
  "new_lamport_timestamp": 59,
  "new_cursor": "018e0000-0000-7000-b000-000000000200"
}
```

### 5.2 REST: Create Transaction

**Request:**
```http
POST /v1/transactions HTTP/1.1
Host: api.taut.id
Authorization: Bearer eyJhbGciOiJSUzI1NiIs...
X-Idempotency-Key: 018e0000-0000-7000-b000-000000000101
Content-Type: application/json

{
  "bank_sampah_id": "018e0000-0000-7000-b000-000000000002",
  "operator_id": "018e0000-0000-7000-b000-000000000003",
  "customer_id": "018e0000-0000-7000-b000-000000000004",
  "items": [
    {
      "category_id": "018e0000-0000-7000-b000-000000000100",
      "weight_grams": 5000
    },
    {
      "category_id": "018e0000-0000-7000-b000-000000000101",
      "weight_grams": 2000
    }
  ],
  "device_timestamp": "2026-06-23T10:30:00+07:00",
  "is_offline_created": false
}
```

**Response (201 Created):**
```json
{
  "id": "018e0000-0000-7000-b000-000000000200",
  "bank_sampah_id": "018e0000-0000-7000-b000-000000000002",
  "operator_id": "018e0000-0000-7000-b000-000000000003",
  "customer_id": "018e0000-0000-7000-b000-000000000004",
  "transaction_type": "deposit",
  "status": "confirmed",
  "total_weight_grams": 7000,
  "total_value": {
    "satuan": 12500,
    "currency": "IDR"
  },
  "items": [
    {
      "category_id": "018e0000-0000-7000-b000-000000000100",
      "category_code": "KRD-01",
      "category_name": "Kardus",
      "weight_grams": 5000,
      "price_per_unit": 1500,
      "total_value": 7500
    },
    {
      "category_id": "018e0000-0000-7000-b000-000000000101",
      "category_code": "PLT-01",
      "category_name": "Botol Plastik",
      "weight_grams": 2000,
      "price_per_unit": 2500,
      "total_value": 5000
    }
  ],
  "device_timestamp": "2026-06-23T10:30:00+07:00",
  "server_timestamp": "2026-06-23T03:30:01Z",
  "created_at": "2026-06-23T03:30:01Z"
}
```

**Response (409 Conflict — Duplicate):**
```json
{
  "error": {
    "code": "DUPLICATE_ENTRY",
    "message": "Transaksi dengan ID ini sudah ada",
    "detail": "X-Idempotency-Key 018e0000-0000-7000-b000-000000000101 sudah diproses",
    "existing_id": "018e0000-0000-7000-b000-000000000200",
    "request_id": "req-abc123"
  }
}
```

### 5.3 REST: Operator Dashboard

**Request:**
```http
GET /v1/dashboard/operator/018e0000-0000-7000-b000-000000000002?period=this_week HTTP/1.1
Host: api.taut.id
Authorization: Bearer eyJhbGciOiJSUzI1NiIs...
```

**Response (200 OK):**
```json
{
  "total_weight_grams": 250000,
  "total_value_satuan": 375000,
  "transaction_count": 42,
  "unique_customers": 18,
  "category_breakdown": [
    {
      "category_id": "018e0000-0000-7000-b000-000000000100",
      "category_name": "Kardus",
      "weight_grams": 120000,
      "value_satuan": 180000,
      "percentage": 48.0
    },
    {
      "category_id": "018e0000-0000-7000-b000-000000000101",
      "category_name": "Botol Plastik",
      "weight_grams": 80000,
      "value_satuan": 120000,
      "percentage": 32.0
    }
  ],
  "daily_trend": [
    {
      "date": "2026-06-17",
      "weight_grams": 35000,
      "value_satuan": 52500,
      "transaction_count": 6
    },
    {
      "date": "2026-06-18",
      "weight_grams": 42000,
      "value_satuan": 63000,
      "transaction_count": 8
    }
  ]
}
```

### 5.4 REST: SMS OTP Request

**Request:**
```http
POST /v1/auth/otp/request HTTP/1.1
Host: api.taut.id
Content-Type: application/json

{
  "phone_number": "08123456789",
  "device_id": "018e0000-0000-7000-b000-000000000001"
}
```

**Response (200 OK):**
```json
{
  "session_id": "018e0000-0000-7000-b000-000000000999",
  "expires_seconds": 300,
  "masked_phone": "0812****789"
}
```

---

## 6. Authentication Flow

### 6.1 Initial Device Registration

```
┌──────────┐         ┌──────────┐        ┌──────────┐       ┌──────────┐
│  Device   │         │  Envoy   │        │  Auth    │       │  SMS     │
│          │         │  Gateway │        │  Service │       │  Gateway │
└────┬─────┘         └────┬─────┘        └────┬─────┘       └────┬─────┘
     │                    │                    │                  │
     │  1. POST /auth/    │                    │                  │
     │     otp/request    │                    │                  │
     │───────────────────▶│───────────────────▶│                 │
     │     (phone, dev)   │                    │                 │
     │                    │                    │ 2. Generate OTP │
     │                    │                    │    + session_id │
     │                    │                    │─────────────────▶│
     │                    │                    │  3. Send SMS     │
     │                    │                    │     "Kode OTP:   │
     │                    │                    │      123456"    │
     │                    │                    │◀─────────────────│
     │  4. Response:      │                    │                 │
     │     session_id     │                    │                 │
     │◀───────────────────│───────────────────│                 │
     │                    │                    │                  │
     │  5. User enters    │                    │                  │
     │     OTP from SMS   │                    │                  │
     │                    │                    │                  │
     │  6. POST /auth/    │                    │                  │
     │     otp/verify     │                    │                  │
     │───────────────────▶│───────────────────▶│                 │
     │     (session,      │                    │  7. Validate     │
     │      otp, pub_key) │                    │     OTP +        │
     │                    │                    │     check expiry │
     │                    │                    │  8. Create user  │
     │                    │                    │     (if new)     │
     │                    │                    │  9. Generate     │
     │                    │                    │     JWT + refresh│
     │                    │                    │  10. Store       │
     │                    │                    │      device_pub  │
     │                    │                    │      _key        │
     │                    │                    │                  │
     │  11. Response:     │                    │                  │
     │      access_token  │                    │                  │
     │      refresh_token │                    │                  │
     │      user          │                    │                  │
     │◀───────────────────│───────────────────│                  │
     │                    │                    │                  │
     │  12. Store token   │                    │                  │
     │      in Android    │                    │                  │
     │      Keystore      │                    │                  │
     │                    │                    │                  │
```

### 6.2 Daily Auth Flow (PIN-based)

```
┌──────────┐               ┌──────────┐
│  Device   │               │  Local   │
│          │               │  SQLCipher│
└────┬─────┘               └────┬─────┘
     │                          │
     │ 1. User opens app        │
     │                          │
     │ 2. Show PIN entry screen │
     │    (4-digit numeric pad) │
     │                          │
     │ 3. User enters PIN       │
     │                          │
     │ 4. bcrypt.verify(        │
     │    input_pin,            │
     │    stored_hash)          │
     │─────────────────────────▶│
     │◀─────────────────────────│
     │     valid/invalid        │
     │                          │
     │  [if valid]              │
     │ 5. Load JWT from         │
     │    Keystore              │
     │                          │
     │ 6. Check JWT expiry      │
     │    - If expired →        │
     │      use refresh_token   │
     │      to get new JWT      │
     │    - If offline → use    │
     │      JWT until expiry    │
     │      + 7 day grace       │
     │                          │
     │ 7. Proceed to home       │
     │    screen                │
     │                          │
     │  [if invalid, count]     │
     │ 8. Increment fail_count  │
     │ 9. If fail_count ≥ 10 →  │
     │    TRIGGER AUTO-WIPE     │
```

### 6.3 Token Structure

**Access Token (JWT):**
```json
// Header
{
  "alg": "RS256",
  "typ": "JWT",
  "kid": "taut-auth-key-v1"
}
// Payload
{
  "sub": "018e0000-0000-7000-b000-000000000003",
  "device_id": "018e0000-0000-7000-b000-000000000001",
  "bank_sampah_id": "018e0000-0000-7000-b000-000000000002",
  "role": "operator",
  "iat": 1800000000,
  "exp": 1800000900,
  "jti": "018e0000-0000-7000-b000-000000000300"
}
```

**Refresh Token (Opaque):**
```
Format: 64 bytes of cryptographically random data
Stored: SHA-256 hash in refresh_tokens table
TTL: 30 days
Rotation: Old refresh token invalidated when new one issued
```

### 6.4 Token Refresh Flow

```
1. Device detects access_token expires_in < 60 seconds (or receives 401)
2. Device sends POST /v1/auth/token/refresh with:
   - refresh_token
   - device_id
   - device_signature = HMAC(refresh_token, device_key)
3. Server verifies:
   a. refresh_token exists and not expired
   b. HMAC signature matches device_pub_key
   c. device_id matches the original issuing device
4. Server generates new access_token (15 min) and new refresh_token (30 day)
5. Server invalidates OLD refresh_token (rotation)
6. Device stores both securely in Android Keystore
```

---

## 7. Error Handling

### 7.1 gRPC Error Codes

| gRPC Code | HTTP Equivalent | Usage |
|-----------|----------------|-------|
| `OK (0)` | 200 | Success |
| `INVALID_ARGUMENT (3)` | 400 | Malformed request, validation failure |
| `UNAUTHENTICATED (16)` | 401 | Missing or expired JWT |
| `PERMISSION_DENIED (7)` | 403 | Valid JWT but insufficient role |
| `NOT_FOUND (5)` | 404 | Entity not found |
| `ALREADY_EXISTS (6)` | 409 | Duplicate UUID (idempotency) |
| `RESOURCE_EXHAUSTED (8)` | 429 | Rate limit exceeded |
| `INTERNAL (13)` | 500 | Server error |
| `UNAVAILABLE (14)` | 503 | Service temporarily unavailable (retry) |
| `DEADLINE_EXCEEDED (4)` | 504 | Request timeout |

### 7.2 REST Error Response Format

```json
// All errors follow this structure:
{
  "error": {
    "code": "ERROR_CODE",
    "message": "Human-readable message in Bahasa Indonesia",
    "detail": "Developer-facing details (optional)",
    "request_id": "req-abc123",
    "validation_errors": [
      {
        "field": "items[0].weight_grams",
        "message": "Berat harus lebih dari 0 gram"
      }
    ]
  }
}
```

### 7.3 Common Error Codes

| Code | HTTP Status | Message | When |
|------|-------------|---------|------|
| `INVALID_PHONE` | 400 | "Nomor telepon tidak valid" | Phone format check failed |
| `OTP_EXPIRED` | 400 | "Kode OTP sudah kedaluwarsa" | OTP > 5 minutes old |
| `OTP_INVALID` | 400 | "Kode OTP salah" | Wrong OTP |
| `OTP_MAX_ATTEMPTS` | 429 | "Terlalu banyak percobaan. Tunggu 5 menit." | >5 wrong OTP attempts |
| `DUPLICATE_ENTRY` | 409 | "Transaksi dengan ID ini sudah ada" | UUID already processed |
| `PIN_INVALID` | 401 | "PIN salah" | Wrong PIN |
| `PIN_LOCKED` | 403 | "AKUN TERKUNCI. Hubungi admin TAUT." | >10 failed PIN attempts |
| `RATE_LIMITED` | 429 | "Terlalu banyak permintaan. Coba lagi nanti." | Rate limit hit |
| `TOKEN_EXPIRED` | 401 | "Sesi sudah habis. Silakan login ulang." | Refresh token expired |
| `INSUFFICIENT_ROLE` | 403 | "Anda tidak memiliki izin untuk aksi ini" | Role check failed |
| `INVALID_SIGNATURE` | 401 | "Tanda tangan digital tidak valid" | HMAC verification failed |
| `FRAUD_SUSPECTED` | 409 | "Transaksi ini terdeteksi mencurigakan. Harap hubungi admin." | Anti-fraud trigger |

---

## 8. Rate Limiting

### 8.1 Per-Device Limits (Mobile App)

```
Sync (gRPC): 100 requests per minute per device
Token Operations: 10 requests per minute per device
Transaction Create: 60 requests per minute per device
All Other: 30 requests per minute per device
```

### 8.2 Per-IP Limits (Web Dashboard)

```
Dashboard API: 300 requests per minute per IP
Export/Download: 10 requests per minute per IP
Auth (OTP): 5 requests per minute per IP
```

### 8.3 Rate Limit Headers

```yaml
RateLimit-Limit: 100           # Maximum requests per window
RateLimit-Remaining: 42        # Remaining requests in current window
RateLimit-Reset: 1800000500    # Unix timestamp when window resets
Retry-After: 5                 # Seconds to wait before retrying (only for 429)
X-RateLimit-Tier: device       # Which rate limit tier was applied
```

### 8.4 Rate Limit Implementation

```
Algorithm: Token bucket
  - Per device_id (or IP for web)
  - Bucket size = rate limit
  - Refill: 1 token per (60s / rate_limit) seconds
  - Burst: up to 2x bucket size allowed for <5 second burst

When exceeded:
  - Response: 429 Too Many Requests (or gRPC RESOURCE_EXHAUSTED)
  - Headers: RateLimit-* headers set
  - No retry: Client must respect Retry-After
  - Repeat offender: After 10 violations in 1 hour → device IP banned for 24h
```

---

## 9. API Versioning

### 9.1 Strategy: URL Prefix Versioning

```
/v1/transactions
/v2/transactions  (future)
```

### 9.2 Version Lifecycle

```
1. Alpha (v1alpha):
   - Not stable; breaking changes daily
   - Only used in development

2. Beta (v1beta):
   - Feature-complete but not hardened
   - Used in staging

3. Stable (v1):
   - GA release
   - Backward-compatible within major version
   - Deprecation: 6 months notice before v2

4. Deprecated (v1):
   - Still working, but no new features
   - Log warning header: "Warning: v1 will be removed in Q1 2027"
   - Removal after 6 month notice
```

### 9.3 Deprecation Process

```
1. Announce deprecation timeline (6 months before)
2. Add Warning header to all v1 responses
3. Maintain v1 alongside v2 for 3 months (dual-run)
4. Remove v1 after confirming no clients use it (monitor API logs)
```

---

*End of API Specification Document*
