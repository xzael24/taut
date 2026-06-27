# TAUT Shared Protobuf Definitions

This directory contains the canonical Protocol Buffer definitions for TAUT.

All `.proto` files use **proto3** syntax and are compatible with gRPC.

## Directory Structure

```
proto/
└── taut/
    └── core/
        └── v1/
            ├── common.proto              # Shared types: Uuid, Timestamp, Money, Weight, enums
            ├── models.proto              # Core domain models: Transaction, WasteCategory, User, Device, etc.
            ├── auth_service.proto        # AuthService: OTP-based phone verification, PIN, device registration
            ├── transaction_service.proto # TransactionService: CRUD for waste transactions
            ├── sync_service.proto        # SyncService: Bidirectional streaming sync for offline-first
            ├── catalog_service.proto     # CatalogService: Waste categories and price references
            └── dashboard_service.proto   # DashboardService: Aggregated statistics for web dashboard
```

## Usage

### Android (Kotlin/Java with gRPC)

```groovy
// In android/build.gradle.kts
protobuf {
    protoc { artifact = "com.google.protobuf:protoc:3.25.5" }
    plugins {
        id("grpc") { artifact = "io.grpc:protoc-gen-grpc-java:1.64.0" }
    }
    generateProtoTasks {
        all().configureEach {
            builtins { id("java") {} }
            plugins { id("grpc") {} }
        }
    }
}
sourceSets {
    main { proto { srcDir("../proto") } }
}
```

### Backend (Kotlin with gRPC)

Point your source set or compilation to `proto/` as the proto include path:

```bash
protoc --proto_path=proto --kotlin_out=build/generated proto/taut/core/v1/*.proto
```

## Compilation

```bash
# Generate Java stubs
protoc --proto_path=proto --java_out=build/generated proto/taut/core/v1/*.proto

# Generate gRPC Java stubs
protoc --proto_path=proto --grpc-java_out=build/generated proto/taut/core/v1/*.proto
```

## Design Principles

- **Offline-first**: UUIDv7 assigned client-side before server sees the transaction
- **Integer-only financials**: All monetary values in satuan rupiah (cents), no floats
- **Weight in grams**: 5 kg = 5000 grams, integer only
- **Lamport clocks**: For conflict-free offline merge without central coordination
- **HMAC signing**: Device-signed transactions for integrity verification
- **Delta sync**: Versioned catalog updates for bandwidth-efficient sync

## Migration from sync-spike

The old spike protos at `sync-spike/proto/taut/sync/v1/` were early prototypes. The canonical definitions now live here at `proto/taut/core/v1/`. Both Android app and backend should reference these files.
