# TAUT Sync Spike — Phase 0 gRPC Bidirectional Sync Protocol

## Overview

This spike proves the bidirectional gRPC sync protocol for TAUT's offline-first architecture. It demonstrates:

- **Bidirectional streaming** via gRPC (`SyncTransactions` RPC)
- **Lamport clock** based causal ordering
- **Conflict resolution** with two strategies:
  - **First-write-wins** for transaction records
  - **LWW (Last-Writer-Wins)** for metadata updates
- **Multi-device sync** with Device A and Device B scenarios

## How to Run

### Prerequisites

- Java 21+ installed
- Gradle (handled via the Gradle wrapper if available, or use the system Gradle)

### 1. Start the Server

```bash
cd sync-spike
./gradlew :server:run
```

The server starts on **port 50051**. You should see:

```
[SERVER] gRPC sync spike server started on port 50051
```

Keep this terminal running.

### 2. Run the Client

In a **separate terminal**, run:

```bash
cd sync-spike
./gradlew :client:run
```

The client will simulate:

1. **Phase 1-2**: Device A creates 3 transactions offline → syncs to server
2. **Phase 3-4**: Device B creates 2 transactions (1 **conflicting** with Device A's) → syncs to server → conflict resolution demonstrated
3. **Phase 5**: Device A re-syncs with a **higher Lamport clock** → LWW override demonstrated

### Expected Output

The client shows:

- Which transactions are **accepted** (✅)
- Which transactions are **rejected** (❌) due to conflicts
- The **conflict resolution instructions** from the server
- **Lamport clock** progression

## What This Spike Proves

| Capability | Status |
|---|---|
| gRPC bidirectional streaming works | ✅ Proved |
| Lamport clock ordering works | ✅ Proved |
| First-write-wins conflict resolution | ✅ Proved |
| LWW (Last-Writer-Wins) metadata merge | ✅ Proved |
| Multi-device sync (Device A → Device B) | ✅ Proved |
| Server-side conflict detection | ✅ Proved |

## Architecture

```
┌─────────────────────┐        ┌──────────────────────┐
│  Device A (client)  │        │  Device B (client)   │
│  ┌───────────────┐  │        │  ┌────────────────┐  │
│  │ Local Store   │  │        │  │ Local Store    │  │
│  │ (offline tx)  │  │        │  │ (offline tx)   │  │
│  └───────┬───────┘  │        │  └───────┬────────┘  │
│          │          │        │          │           │
│          ▼          │        │          ▼           │
│  ┌───────────────┐  │        │  ┌────────────────┐  │
│  │ gRPC Client   │  │        │  │ gRPC Client    │  │
│  └───────┬───────┘  │        │  └───────┬────────┘  │
└──────────┼──────────┘        └──────────┼───────────┘
           │    Bidirectional Stream       │
           │    SyncTransactions RPC       │
           ▼                               ▼
      ┌────────────────────────────────────────┐
      │           gRPC Server (:50051)          │
      │  ┌──────────────┐  ┌─────────────────┐  │
      │  │ SyncService   │  │ ConflictResolver│  │
      │  │ (impl)       │  │ (FWW + LWW)    │  │
      │  └──────┬───────┘  └────────┬────────┘  │
      │         ▼                   ▼            │
      │  ┌──────────────────────────────────┐    │
      │  │        InMemoryStore             │    │
      │  │        (HashMap "DB")           │    │
      │  └──────────────────────────────────┘    │
      └────────────────────────────────────────┘
```

## Project Structure

```
sync-spike/
├── proto/
│   └── taut/sync/v1/
│       ├── models.proto   # Shared data types (Transaction, SyncMetadata)
│       └── sync.proto     # gRPC service definition (TransactionSync)
├── server/
│   ├── build.gradle.kts   # Server build config with protobuf plugin
│   └── src/main/kotlin/taut/sync/spike/
│       ├── Main.kt             # Server entry point (port 50051)
│       ├── SyncServiceImpl.kt  # gRPC service implementation
│       ├── ConflictResolver.kt # FWW + LWW conflict resolution
│       └── InMemoryStore.kt    # Spike database (HashMap)
├── client/
│   ├── build.gradle.kts   # Client build config
│   └── src/main/kotlin/taut/sync/spike/client/
│       └── Main.kt        # Client simulation (Device A + Device B)
├── settings.gradle.kts    # Multi-project settings
└── README.md
```

## Risks That Remain

| Risk | Notes |
|---|---|
| **CRDT-based merging** | Not implemented — using FWW + LWW is simpler but less sophisticated than CRDTs for concurrent edits |
| **Network partitions** | Spike tests on localhost only; real-world network failures not simulated |
| **Large payloads** | No pagination or chunking for large transaction batches |
| **Authentication** | No auth layer — assumes trusted devices |
| **Persistence** | InMemoryStore is lost on restart; real design needs SQLite/RocksDB |
| **Clock skew** | Lamport clocks assume monotonic increase; device clock resets not handled |
| **Stream backpressure** | No flow control on the bidirectional stream |
| **Reconnection** | No retry/reconnect logic |

## gRPC Service Definition

```protobuf
service TransactionSync {
  // Bidirectional streaming sync
  rpc SyncTransactions(stream SyncRequest) returns (stream SyncResponse);
}

message SyncRequest {
  string device_id = 1;
  SyncMetadata metadata = 2;
  repeated Transaction transactions = 3;
}

message SyncResponse {
  bool server_ack = 1;
  string conflict_resolution_instructions = 2;
  SyncMetadata server_state = 3;
  repeated Transaction accepted_transactions = 4;
  repeated Transaction rejected_transactions = 5;
}
```

## Conflict Resolution Strategy

### First-Write-Wins (Transactions)

When two devices submit a transaction with the same ID, the server keeps the **first one it received**. The second is rejected with a conflict instruction.

### Last-Writer-Wins (Metadata)

If a device re-submits a conflicting transaction with a **higher Lamport timestamp**, the server accepts it as an LWW update. This allows collaborative editing where the most recent writer's version wins.
