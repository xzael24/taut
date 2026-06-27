# TAUT API Reference

> Version: 1.0.0  
> Base URL: `http://<host>:9000/v1`  
> Protocol: HTTP REST + gRPC bidirectional streaming

This document describes all API endpoints exposed by the TAUT backend. The backend runs on **Kotlin 2.1.20** with **Ktor 2.3.13**, serving both REST APIs and gRPC on port `9000`.

---

## Authentication

TAUT uses **JWT-based authentication** with a two-token system:
- **Access Token** (short-lived, 15 minutes): Sent in `Authorization: Bearer <token>` header
- **Refresh Token** (long-lived, 30 days): Sent in `X-Refresh-Token` header or request body
- **Device Binding**: All tokens are bound to a specific device via `device_id` and device signature

### Endpoints Overview

| Endpoint | Method | Auth Required | Description |
|---|---|---|---|
| `/v1/auth/otp/request` | POST | No | Request SMS OTP for phone verification |
| `/v1/auth/otp/verify` | POST | No | Verify OTP and receive tokens |
| `/v1/auth/token/refresh` | POST | No (token req.) | Refresh access token |
| `/v1/auth/pin/change` | POST | Yes | Change operator PIN |
| `/v1/auth/pin/verify` | POST | Yes | Verify operator PIN server-side |

---

### POST /v1/auth/otp/request

Request an OTP to be sent via SMS for phone number verification.

#### Request Body

```json
{
  "phone_number": "+6281234567890",
  "device_id": "device-uuid-here",
  "bank_sampah_id": "bank-id-optional"
}
```

| Field | Type | Required | Description |
|---|---|---|---|
| `phone_number` | String | Yes | Indonesian phone number (E.164 format) |
| `device_id` | String | Yes | Unique device identifier |
| `bank_sampah_id` | String | No | Bank Sampah ID (for first-time setup) |

#### Response (200 OK)

```json
{
  "session_id": "otp-session-uuid",
  "expires_in": 300,
  "message": "OTP telah dikirim ke nomor Anda."
}
```

#### Error Responses

| Status | Code | Description |
|---|---|---|
| 400 | `INVALID_BODY` | Invalid request body |
| 429 | `RATE_LIMITED` | Too many OTP requests (rate limit exceeded) |

---

### POST /v1/auth/otp/verify

Verify the OTP code and obtain JWT access and refresh tokens.

#### Request Body

```json
{
  "session_id": "otp-session-uuid",
  "otp_code": "123456",
  "device_id": "device-uuid-here",
  "device_pub_key": "base64-encoded-public-key"
}
```

| Field | Type | Required | Description |
|---|---|---|---|
| `session_id` | String | Yes | Session ID from OTP request |
| `otp_code` | String | Yes | 6-digit OTP code |
| `device_id` | String | Yes | Device UUID |
| `device_pub_key` | String | Yes | Device's RSA public key (base64) |

#### Response (200 OK)

```json
{
  "access_token": "eyJhbGciOiJIUzI1NiIs...",
  "refresh_token": "eyJhbGciOiJIUzI1NiIs...",
  "token_type": "Bearer",
  "expires_in": 900,
  "operator": {
    "id": "operator-uuid",
    "name": "Operator Name",
    "bank_sampah_id": "bank-uuid"
  }
}
```

#### Error Responses

| Status | Code | Description |
|---|---|---|
| 400 | `INVALID_BODY` | Invalid request body |
| 400 | `INVALID_OTP` | Incorrect OTP code |
| 410 | `OTP_EXPIRED` | OTP has expired |
| 410 | `OTP_ALREADY_USED` | OTP has already been verified |

---

### POST /v1/auth/token/refresh

Refresh an expired or expiring access token using a refresh token.

#### Request Headers (alternative to body)
```
X-Refresh-Token: eyJhbGciOiJIUzI1NiIs...
X-Device-ID: device-uuid-here
X-Device-Signature: base64-signature
X-Idempotency-Key: unique-key (optional)
```

#### Request Body

```json
{
  "refresh_token": "eyJhbGciOiJIUzI1NiIs...",
  "device_id": "device-uuid-here",
  "device_signature": "base64-signature"
}
```

| Field | Type | Required | Description |
|---|---|---|---|
| `refresh_token` | String | Yes | Refresh token from previous auth |
| `device_id` | String | Yes | Device UUID |
| `device_signature` | String | Yes | Signed challenge for device binding |

#### Response (200 OK)

```json
{
  "access_token": "eyJhbGciOiJIUzI1NiIs...",
  "refresh_token": "eyJhbGciOiJIUzI1NiIs...",
  "token_type": "Bearer",
  "expires_in": 900
}
```

#### Error Responses

| Status | Code | Description |
|---|---|---|
| 400 | `INVALID_BODY` | Invalid request body |
| 401 | `TOKEN_EXPIRED` | Refresh token expired (re-auth required) |
| 400 | `INVALID_TOKEN` | Malformed or invalid token |
| 400 | `DEVICE_MISMATCH` | Device does not match token binding |

---

### POST /v1/auth/pin/change

Change the operator's local PIN. This is an idempotent operation.

#### Request Headers
```
Authorization: Bearer <access_token>
X-Idempotency-Key: unique-key (optional)
```

#### Request Body

```json
{
  "user_id": "operator-uuid",
  "old_pin": "1234",
  "new_pin": "5678",
  "idempotency_key": "unique-key-for-retry"
}
```

| Field | Type | Required | Description |
|---|---|---|---|
| `user_id` | String | Yes | Operator ID |
| `old_pin` | String | Yes | Current PIN (6 digits) |
| `new_pin` | String | Yes | New PIN (6 digits) |
| `idempotency_key` | String | No | Prevents duplicate PIN changes |

#### Response (200 OK)

```json
{
  "message": "PIN berhasil diubah.",
  "changed_at": "2026-06-25T10:30:00Z"
}
```

---

### POST /v1/auth/pin/verify

Server-side PIN verification with rate limiting (5 attempts, then 30-min lockout).

#### Request Headers
```
Authorization: Bearer <access_token>
```

#### Request Body

```json
{
  "user_id": "operator-uuid",
  "pin": "1234"
}
```

#### Response (200 OK)

```json
{
  "verified": true,
  "remaining_attempts": 4,
  "lockout_until": null
}
```

#### Response (200 OK - Invalid PIN)

```json
{
  "verified": false,
  "remaining_attempts": 2,
  "lockout_until": null
}
```

#### Error Responses

| Status | Code | Description |
|---|---|---|
| 403 | `PIN_LOCKED` | PIN locked due to too many attempts |
| 400 | `INVALID_BODY` | Invalid request body |

---

## Transactions

### Endpoints Overview

| Endpoint | Method | Auth Required | Description |
|---|---|---|---|
| `/v1/transactions` | POST | Yes | Create a new transaction |
| `/v1/transactions/{id}` | GET | Yes | Get transaction by ID |
| `/v1/transactions/{id}/void` | POST | Yes | Void a transaction (admin) |
| `/v1/banks/{bank_id}/transactions` | GET | Yes | List transactions by bank |
| `/v1/customers/{id}/transactions` | GET | Yes | List customer transactions |

---

### POST /v1/transactions

Create a new waste transaction. Supports idempotency via `X-Idempotency-Key` header.

#### Request Headers
```
Authorization: Bearer <access_token>
X-Idempotency-Key: unique-key (optional)
```

#### Request Body

```json
{
  "bank_sampah_id": "bank-uuid",
  "operator_id": "operator-uuid",
  "customer_phone": "+6281234567890",
  "customer_name": "Customer Name",
  "transaction_type": "setoran",
  "items": [
    {
      "category_id": "category-uuid",
      "weight_kg": 2.5,
      "price_per_kg": 3000
    }
  ]
}
```

| Field | Type | Required | Description |
|---|---|---|---|
| `bank_sampah_id` | String | Yes | Bank Sampah identifier |
| `operator_id` | String | Yes | Operator creating the transaction |
| `customer_phone` | String | Yes | Customer phone number |
| `customer_name` | String | No | Customer display name |
| `transaction_type` | String | Yes | `setoran` (deposit) or `penarikan` (withdrawal) |
| `items` | Array | Yes | List of waste items |

#### Item Object

| Field | Type | Required | Description |
|---|---|---|---|
| `category_id` | String | Yes | Waste category ID |
| `weight_kg` | Number | Yes | Weight in kilograms |
| `price_per_kg` | Number | Yes | Price per kilogram (from catalog) |

#### Response (201 Created)

```json
{
  "id": "transaction-uuid",
  "total": 7500,
  "items_count": 1,
  "created_at": "2026-06-25T10:30:00Z",
  "status": "completed"
}
```

#### Error Responses

| Status | Code | Description |
|---|---|---|
| 400 | `INVALID_BODY` | Invalid request body |
| 409 | `DUPLICATE` | Duplicate transaction (idempotency) |
| 401 | `UNAUTHORIZED` | Missing or invalid token |

---

### GET /v1/transactions/{id}

Get a single transaction with all its items.

#### Request Headers
```
Authorization: Bearer <access_token>
```

#### Response (200 OK)

```json
{
  "id": "transaction-uuid",
  "bank_sampah_id": "bank-uuid",
  "operator_id": "operator-uuid",
  "customer_id": "customer-uuid",
  "customer_name": "Customer Name",
  "transaction_type": "setoran",
  "items": [
    {
      "id": "item-uuid",
      "category_id": "category-uuid",
      "category_name": "Plastik Campuran",
      "weight_kg": 2.5,
      "price_per_kg": 3000,
      "subtotal": 7500
    }
  ],
  "total": 7500,
  "status": "completed",
  "created_at": "2026-06-25T10:30:00Z",
  "voided_at": null,
  "void_reason": null
}
```

#### Error Responses

| Status | Code | Description |
|---|---|---|
| 400 | `MISSING_PARAM` | Transaction ID required |
| 404 | `NOT_FOUND` | Transaction not found |

---

### POST /v1/transactions/{id}/void

Void a transaction (admin-only operation).

#### Request Headers
```
Authorization: Bearer <access_token>
X-Idempotency-Key: unique-key (optional)
```

#### Request Body

```json
{
  "reason": "Customer requested cancellation",
  "admin_id": "admin-uuid"
}
```

| Field | Type | Required | Description |
|---|---|---|---|
| `reason` | String | No | Reason for voiding |
| `admin_id` | String | No | Admin performing the void |

#### Response (200 OK)

```json
{
  "id": "transaction-uuid",
  "status": "voided",
  "voided_at": "2026-06-25T11:00:00Z",
  "void_reason": "Customer requested cancellation"
}
```

#### Error Responses

| Status | Code | Description |
|---|---|---|
| 404 | `NOT_FOUND` | Transaction not found |
| 409 | `ALREADY_VOIDED` | Transaction already voided |

---

### GET /v1/banks/{bank_id}/transactions

List transactions for a specific bank with filtering and pagination.

#### Request Headers
```
Authorization: Bearer <access_token>
```

#### Query Parameters

| Parameter | Type | Default | Description |
|---|---|---|---|
| `from_date` | String (ISO 8601) | — | Start date for filter |
| `to_date` | String (ISO 8601) | — | End date for filter |
| `type` | String | — | `setoran` or `penarikan` |
| `status` | String | — | `completed`, `voided`, `pending` |
| `page` | Integer | 1 | Page number |
| `page_size` | Integer | 20 | Items per page (max: 100) |

#### Response (200 OK)

```json
{
  "data": [
    {
      "id": "transaction-uuid",
      "customer_name": "Customer Name",
      "transaction_type": "setoran",
      "total": 7500,
      "status": "completed",
      "created_at": "2026-06-25T10:30:00Z"
    }
  ],
  "pagination": {
    "page": 1,
    "page_size": 20,
    "total_count": 42,
    "total_pages": 3
  }
}
```

---

### GET /v1/customers/{id}/transactions

List transaction history for a specific customer.

#### Request Headers
```
Authorization: Bearer <access_token>
```

#### Query Parameters

| Parameter | Type | Default | Description |
|---|---|---|---|
| `page` | Integer | 1 | Page number |
| `page_size` | Integer | 20 | Items per page (max: 100) |

#### Response (200 OK)

Same structure as bank transactions listing.

---

## Catalog

### Endpoints Overview

| Endpoint | Method | Auth Required | Description |
|---|---|---|---|
| `/v1/categories` | GET | Yes | List all active waste categories |
| `/v1/prices` | GET | Yes | Get current price catalog |
| `/v1/categories/{id}/prices/history` | GET | Yes | Price history for category |

---

### GET /v1/categories

List all active waste categories.

#### Response (200 OK)

```json
{
  "categories": [
    {
      "id": "category-uuid",
      "name": "Plastik Campuran",
      "unit": "kg",
      "description": "Botol plastik, kemasan plastik, plastik keras",
      "status": "active"
    }
  ]
}
```

---

### GET /v1/prices

Get the current price catalog, optionally filtered by region and version.

#### Query Parameters

| Parameter | Type | Description |
|---|---|---|
| `region_id` | Integer | Filter by region |
| `last_version` | Integer | Get only changes since this version (for delta sync) |

#### Response (200 OK)

```json
{
  "prices": [
    {
      "category_id": "category-uuid",
      "category_name": "Plastik Campuran",
      "price_per_kg": 3000,
      "region_id": 1,
      "effective_date": "2026-06-01",
      "version": 12
    }
  ],
  "current_version": 12,
  "updated_at": "2026-06-01T00:00:00Z"
}
```

---

### GET /v1/categories/{id}/prices/history

Get historical price changes for a specific category.

#### Query Parameters

| Parameter | Type | Default | Description |
|---|---|---|---|
| `region_id` | Integer | — | Filter by region |
| `from_date` | String (ISO 8601) | — | Start date |
| `to_date` | String (ISO 8601) | — | End date |
| `page` | Integer | 1 | Page number |
| `page_size` | Integer | 20 | Items per page |

#### Response (200 OK)

```json
{
  "data": [
    {
      "price_per_kg": 3000,
      "effective_date": "2026-06-01",
      "version": 12
    },
    {
      "price_per_kg": 2500,
      "effective_date": "2026-05-01",
      "version": 11
    }
  ],
  "pagination": {
    "page": 1,
    "page_size": 20,
    "total_count": 4,
    "total_pages": 1
  }
}
```

---

## Dashboard

### Endpoints Overview

| Endpoint | Method | Auth Required | Description |
|---|---|---|---|
| `/v1/dashboard/operator` | GET | Yes | Operator statistics |
| `/v1/dashboard/bank` | GET | Yes | Bank-wide statistics |

---

### GET /v1/dashboard/operator

Get statistics for the authenticated operator.

#### Response (200 OK)

```json
{
  "today_transactions": 15,
  "today_total_kg": 42.5,
  "today_total_value": 127500,
  "weekly_transactions": 98,
  "monthly_transactions": 312,
  "last_sync": "2026-06-25T10:29:00Z",
  "pending_sync_count": 0
}
```

---

### GET /v1/dashboard/bank

Get statistics for the operator's bank.

#### Response (200 OK)

```json
{
  "total_operators": 5,
  "total_customers": 234,
  "today_transactions": 67,
  "today_total_kg": 185.3,
  "today_total_value": 556000,
  "weekly_transactions": 412,
  "monthly_transactions": 1845,
  "popular_categories": [
    {
      "category_name": "Plastik Campuran",
      "total_kg": 85.5,
      "percentage": 46.1
    }
  ]
}
```

---

## Devices

### Endpoints Overview

| Endpoint | Method | Auth Required | Description |
|---|---|---|---|
| `/v1/devices/register` | POST | Yes | Register a new device |
| `/v1/devices/update` | POST | Yes | Update device metadata |

---

### POST /v1/devices/register

Register a device during first-time setup.

#### Request Headers
```
Authorization: Bearer <access_token>
```

#### Request Body

```json
{
  "device_id": "device-uuid-here",
  "device_name": "Redmi A2 Operator 1",
  "public_key": "base64-encoded-public-key",
  "push_token": "fcm-push-token-optional"
}
```

#### Response (200 OK)

```json
{
  "device_id": "device-uuid-here",
  "status": "registered",
  "registered_at": "2026-06-25T10:30:00Z"
}
```

---

### POST /v1/devices/update

Update device metadata (name, push token, etc.).

#### Request Headers
```
Authorization: Bearer <access_token>
```

#### Request Body

```json
{
  "device_id": "device-uuid-here",
  "device_name": "Updated Name",
  "push_token": "new-fcm-token"
}
```

#### Response (200 OK)

```json
{
  "device_id": "device-uuid-here",
  "status": "updated",
  "updated_at": "2026-06-25T11:00:00Z"
}
```

---

## Sync

### Overview

TAUT uses **gRPC bidirectional streaming** for efficient data synchronization between the Android client and the server. The sync protocol is defined in `proto/taut/core/v1/sync_service.proto`.

### gRPC Service Definition

```protobuf
service SyncService {
  // Bidirectional streaming for real-time sync
  rpc Sync(stream SyncRequest) returns (stream SyncResponse);
}
```

#### SyncRequest

```protobuf
message SyncRequest {
  string device_id = 1;
  string access_token = 2;
  SyncType sync_type = 3;
  repeated Transaction transactions = 4;
  int64 last_sync_timestamp = 5;
}

enum SyncType {
  FULL = 0;
  DELTA = 1;
  PUSH = 2;
  PULL = 3;
}
```

#### SyncResponse

```protobuf
message SyncResponse {
  bool success = 1;
  string error_message = 2;
  repeated Transaction transactions = 3;
  repeated CatalogUpdate catalog_updates = 4;
  int64 server_timestamp = 5;
}
```

### Sync Flow

1. **Connect**: Client establishes gRPC bidi-streaming connection
2. **Authenticate**: Client sends device_id + access_token
3. **Push**: Client sends pending local transactions
4. **Pull**: Server sends any pending updates for this device
5. **Acknowledge**: Both sides confirm receipt and update sync metadata
6. **Heartbeat**: Connection kept alive with periodic pings

---

## Health

### Endpoints Overview

| Endpoint | Method | Auth Required | Description |
|---|---|---|---|
| `/health` | GET | No | Service health check |

---

### GET /health

Simple health check endpoint. Used by Docker HEALTHCHECK and monitoring systems.

#### Response (200 OK)

```json
{
  "status": "healthy",
  "version": "1.0.0",
  "uptime_seconds": 3600,
  "database": "connected",
  "redis": "connected"
}
```

#### Response (503 Service Unavailable)

```json
{
  "status": "unhealthy",
  "version": "1.0.0",
  "uptime_seconds": 120,
  "database": "disconnected",
  "database_error": "Connection refused",
  "redis": "connected"
}
```

---

## Error Handling

All API errors follow a consistent JSON format:

```json
{
  "error": "ERROR_CODE",
  "message": "Human-readable description in Bahasa Indonesia."
}
```

### Common Error Codes

| HTTP Status | Error Code | Description |
|---|---|---|
| 400 | `INVALID_BODY` | Request body could not be parsed |
| 400 | `MISSING_PARAM` | Required parameter missing |
| 401 | `UNAUTHORIZED` | Missing or invalid JWT token |
| 403 | `FORBIDDEN` | Insufficient permissions |
| 404 | `NOT_FOUND` | Resource not found |
| 409 | `DUPLICATE` | Duplicate request (idempotency) |
| 409 | `ALREADY_VOIDED` | Transaction already voided |
| 410 | `OTP_EXPIRED` | OTP code has expired |
| 429 | `RATE_LIMITED` | Rate limit exceeded |
| 500 | `INTERNAL_ERROR` | Unexpected server error |

---

## Rate Limiting

| Endpoint | Limit | Window |
|---|---|---|
| `/v1/auth/otp/request` | 3 requests | Per phone, per 5 minutes |
| `/v1/auth/otp/verify` | 10 attempts | Per session |
| `/v1/auth/pin/verify` | 5 attempts | Per user, then 30-min lockout |
| All other endpoints | 100 requests | Per device, per minute |

---

## gRPC Endpoints

In addition to REST APIs, TAUT exposes gRPC services on the same port (9000) for efficient mobile communication:

| Service | File | RPC | Description |
|---|---|---|---|
| `AuthService` | `auth_service.proto` | `Login`, `RefreshToken`, `VerifyPin` | Authentication |
| `TransactionService` | `transaction_service.proto` | `Create`, `Get`, `List`, `Void` | Transaction ops |
| `CatalogService` | `catalog_service.proto` | `GetCategories`, `GetPrices` | Catalog queries |
| `DashboardService` | `dashboard_service.proto` | `GetOperatorStats`, `GetBankStats` | Statistics |
| `SyncService` | `sync_service.proto` | `Sync` (bidi-streaming) | Data sync |

### Connecting via gRPC

```kotlin
// Kotlin client (Android)
val channel = ManagedChannelBuilder.forAddress(host, port)
    .usePlaintext() // Use TLS in production
    .build()

val stub = SyncServiceGrpcKt.SyncServiceCoroutineStub(channel)

// Bidirectional streaming
val flow = stub.sync(syncRequestFlow)
```
