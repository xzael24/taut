"""
TAUT gRPC Sync Protocol — Server POC
======================================
Simulates the backend sync server with:
- In-memory entity store with Lamport clock versioning
- Conflict detection and resolution (LWW, FWW, manual)
- Bidirectional streaming for multiple concurrent clients
- Support for 2+ clients editing the same entity
"""
from __future__ import annotations

import logging
import threading
import time
import uuid
from concurrent import futures
from dataclasses import dataclass, field
from typing import Dict, List, Optional, Tuple

import grpc

# Generated protobuf stubs
import common_pb2
import sync_pb2
import sync_pb2_grpc

logger = logging.getLogger(__name__)

# ──────────────────────────────────────────────
# SyncStatus constants (protobuf 6.x compat)
# ──────────────────────────────────────────────
SyncStatus = sync_pb2.SyncStatus
SYNC_STATUS_ACCEPTED = SyncStatus.ACCEPTED
SYNC_STATUS_CONFLICT_RESOLVED = SyncStatus.CONFLICT_RESOLVED
SYNC_STATUS_CONFLICT_MANUAL = SyncStatus.CONFLICT_MANUAL
SYNC_STATUS_REJECTED = SyncStatus.REJECTED
SYNC_STATUS_DUPLICATE = SyncStatus.DUPLICATE

# ConflictResolutionStrategy constants
CRStrategy = common_pb2.ConflictResolutionStrategy
CR_LWW = CRStrategy.LAST_WRITER_WINS
CR_FWW = CRStrategy.FIRST_WRITE_WINS
CR_MANUAL = CRStrategy.MANUAL
CR_MERGE = CRStrategy.MERGE_FIELDS

# EntityType constants
EntityType = common_pb2.EntityType
ET_TRANSACTION = EntityType.TRANSACTION
ET_PROFILE = EntityType.PROFILE
ET_SETTING = EntityType.SETTING
ET_CATEGORY = EntityType.CATEGORY
ET_SYNC_LOG = EntityType.SYNC_LOG

# ──────────────────────────────────────────────
# In-memory entity store
# ──────────────────────────────────────────────

@dataclass
class EntityState:
    """The authoritative state of an entity on the server."""
    entity_id: str
    entity_type: int  # EntityType enum value
    fields: Dict[str, str] = field(default_factory=dict)
    clock: int = 0           # highest Lamport timestamp that touched this entity
    node_id: str = ""        # node that last modified it
    client_timestamp_ms: int = 0
    transaction_id: str = ""  # UUIDv7 of the transaction that created/updated it
    status: int = SYNC_STATUS_ACCEPTED


class ConflictResolutionStrategy:
    """Resolution strategies matching the protobuf enum."""
    LWW = CR_LWW       # highest clock wins
    FWW = CR_FWW        # first accepted wins
    MANUAL = CR_MANUAL  # flag for review
    MERGE = CR_MERGE    # field-level merge


# Entity-type to default resolution strategy mapping
ENTITY_RESOLUTION_MAP = {
    ET_TRANSACTION: ConflictResolutionStrategy.FWW,   # append-only ledger
    ET_PROFILE: ConflictResolutionStrategy.LWW,       # LWW field-merge
    ET_SETTING: ConflictResolutionStrategy.MANUAL,    # manual review
    ET_CATEGORY: ConflictResolutionStrategy.LWW,      # server-authoritative LWW
    ET_SYNC_LOG: ConflictResolutionStrategy.FWW,      # append-only
}


class EntityStore:
    """Thread-safe in-memory store for all server entities."""

    def __init__(self):
        self._lock = threading.Lock()
        self._entities: Dict[str, EntityState] = {}
        self._server_clock = 0
        self._processed_transactions: set = set()  # UUIDv7 dedup set
        self._idempotency_cache: Dict[str, sync_pb2.SyncResponse] = {}

    def tick_clock(self) -> int:
        """Advance and return the server's Lamport clock."""
        with self._lock:
            self._server_clock += 1
            return self._server_clock

    def get_current_clock(self) -> int:
        with self._lock:
            return self._server_clock

    def is_duplicate(self, transaction_id: str) -> bool:
        with self._lock:
            return transaction_id in self._processed_transactions

    def mark_processed(self, transaction_id: str) -> None:
        with self._lock:
            self._processed_transactions.add(transaction_id)

    def get_cached_response(self, transaction_id: str) -> Optional[sync_pb2.SyncResponse]:
        with self._lock:
            return self._idempotency_cache.get(transaction_id)

    def cache_response(self, transaction_id: str, response: sync_pb2.SyncResponse) -> None:
        with self._lock:
            self._idempotency_cache[transaction_id] = response

    def get_entity(self, entity_id: str) -> Optional[EntityState]:
        with self._lock:
            return self._entities.get(entity_id)

    def upsert_entity(self, state: EntityState) -> None:
        with self._lock:
            self._entities[state.entity_id] = state

    def resolve_conflict(
        self,
        request: sync_pb2.SyncRequest,
    ) -> Tuple[sync_pb2.SyncResponse, EntityState]:
        """
        Process a single SyncRequest: dedup → detect conflict → resolve → persist.
        Returns (SyncResponse, updated EntityState).
        """
        # ── 1. Dedup ──
        if self.is_duplicate(request.transaction_id):
            cached = self.get_cached_response(request.transaction_id)
            if cached:
                return cached, self.get_entity(request.entity_id) or EntityState(
                    entity_id=request.entity_id, entity_type=request.entity_type
                )
            return (
                self._make_response(
                    request,
                    status=SYNC_STATUS_DUPLICATE,
                    message=f"Duplicate transaction: {request.transaction_id}",
                    server_clock=self.get_current_clock(),
                ),
                self.get_entity(request.entity_id) or EntityState(
                    entity_id=request.entity_id, entity_type=request.entity_type
                ),
            )

        self.mark_processed(request.transaction_id)

        # ── 2. Check existing entity ──
        existing = self.get_entity(request.entity_id)
        client_clock = request.clock.timestamp

        if existing is None:
            # New entity — always accept
            server_clock = max(self.tick_clock(), client_clock)
            entity = EntityState(
                entity_id=request.entity_id,
                entity_type=request.entity_type,
                fields=dict(request.fields),
                clock=server_clock,
                node_id=request.client_id,
                client_timestamp_ms=request.client_timestamp_ms,
                transaction_id=request.transaction_id,
                status=SYNC_STATUS_ACCEPTED,
            )
            self.upsert_entity(entity)
            resp = self._make_response(
                request,
                status=SYNC_STATUS_ACCEPTED,
                message="Accepted — new entity created",
                server_clock=server_clock,
            )
            self.cache_response(request.transaction_id, resp)
            return resp, entity

        # ── 3. Conflict detection ──
        strategy = ENTITY_RESOLUTION_MAP.get(
            request.entity_type, ConflictResolutionStrategy.LWW
        )

        conflicts: List[common_pb2.ConflictInfo] = []
        resolved_fields: Dict[str, str] = {}

        if strategy == ConflictResolutionStrategy.FWW:
            # First-write-wins: existing entity stays; client gets told
            for field, client_val in request.fields.items():
                server_val = existing.fields.get(field, "")
                if server_val != client_val and server_val:
                    conflicts.append(common_pb2.ConflictInfo(
                        field_name=field,
                        client_value=client_val,
                        server_value=server_val,
                        resolved_value=server_val,  # server wins
                        resolution_strategy="FWW (first-write-wins)",
                        client_clock=common_pb2.LamportClock(
                            timestamp=client_clock, node_id=request.client_id
                        ),
                        server_clock=common_pb2.LamportClock(
                            timestamp=existing.clock, node_id=existing.node_id
                        ),
                    ))
                    resolved_fields[field] = server_val
                else:
                    resolved_fields[field] = client_val

            # For FWW: we do NOT modify the entity (append-only)
            server_clock = self.tick_clock()
            resp_status = SYNC_STATUS_CONFLICT_RESOLVED if conflicts else SYNC_STATUS_ACCEPTED

        elif strategy == ConflictResolutionStrategy.LWW:
            # Last-writer-wins: the higher Lamport clock wins per field
            server_clock = max(self.tick_clock(), client_clock)
            merged_fields = dict(existing.fields)

            for field, client_val in request.fields.items():
                server_val = existing.fields.get(field, "")
                if server_val != client_val and server_val != "":
                    # Conflict — resolve by clock comparison
                    if client_clock > existing.clock:
                        # Client wins
                        conflicts.append(common_pb2.ConflictInfo(
                            field_name=field,
                            client_value=client_val,
                            server_value=server_val,
                            resolved_value=client_val,
                            resolution_strategy="LWW (last-writer-wins) — client newer",
                            client_clock=common_pb2.LamportClock(
                                timestamp=client_clock, node_id=request.client_id
                            ),
                            server_clock=common_pb2.LamportClock(
                                timestamp=existing.clock, node_id=existing.node_id
                            ),
                        ))
                        merged_fields[field] = client_val
                    else:
                        # Server wins
                        conflicts.append(common_pb2.ConflictInfo(
                            field_name=field,
                            client_value=client_val,
                            server_value=server_val,
                            resolved_value=server_val,
                            resolution_strategy="LWW (last-writer-wins) — server newer",
                            client_clock=common_pb2.LamportClock(
                                timestamp=client_clock, node_id=request.client_id
                            ),
                            server_clock=common_pb2.LamportClock(
                                timestamp=existing.clock, node_id=existing.node_id
                            ),
                        ))
                        merged_fields[field] = server_val
                else:
                    merged_fields[field] = client_val

            # Update entity with merged state
            existing.fields = merged_fields
            existing.clock = server_clock
            existing.node_id = request.client_id
            existing.client_timestamp_ms = request.client_timestamp_ms
            existing.transaction_id = request.transaction_id
            existing.status = SYNC_STATUS_CONFLICT_RESOLVED if conflicts else SYNC_STATUS_ACCEPTED
            self.upsert_entity(existing)

            resolved_fields = merged_fields
            resp_status = SYNC_STATUS_CONFLICT_RESOLVED if conflicts else SYNC_STATUS_ACCEPTED

        elif strategy == ConflictResolutionStrategy.MANUAL:
            # Flag for manual review — return both values
            server_clock = self.tick_clock()
            for field, client_val in request.fields.items():
                server_val = existing.fields.get(field, "")
                if server_val != client_val and server_val != "":
                    conflicts.append(common_pb2.ConflictInfo(
                        field_name=field,
                        client_value=client_val,
                        server_value=server_val,
                        resolved_value="PENDING_MANUAL_REVIEW",
                        resolution_strategy="MANUAL (requires operator decision)",
                        client_clock=common_pb2.LamportClock(
                            timestamp=client_clock, node_id=request.client_id
                        ),
                        server_clock=common_pb2.LamportClock(
                            timestamp=existing.clock, node_id=existing.node_id
                        ),
                    ))
                    resolved_fields[field] = client_val  # store tentatively
                else:
                    resolved_fields[field] = client_val
            resp_status = SYNC_STATUS_CONFLICT_MANUAL if conflicts else SYNC_STATUS_ACCEPTED

        else:
            server_clock = self.tick_clock()
            resp_status = SYNC_STATUS_REJECTED

        resp = self._make_response(
            request,
            status=resp_status,
            message=f"{'Resolved ' if conflicts else ''}{len(conflicts)} conflict(s){' (manual)' if resp_status == SYNC_STATUS_CONFLICT_MANUAL else ''}",
            server_clock=server_clock,
            conflicts=conflicts,
            resolved_fields=resolved_fields,
        )
        self.cache_response(request.transaction_id, resp)
        return resp, existing if existing else entity

    @staticmethod
    def _make_response(
        request: sync_pb2.SyncRequest,
        status: int,
        message: str,
        server_clock: int,
        conflicts: Optional[List[common_pb2.ConflictInfo]] = None,
        resolved_fields: Optional[Dict[str, str]] = None,
    ) -> sync_pb2.SyncResponse:
        resp = sync_pb2.SyncResponse(
            transaction_id=request.transaction_id,
            entity_id=request.entity_id,
            status=status,
            message=message,
            server_clock=common_pb2.LamportClock(
                timestamp=server_clock, node_id="server"
            ),
        )
        if conflicts:
            resp.conflicts.extend(conflicts)
        if resolved_fields:
            for k, v in resolved_fields.items():
                resp.resolved_fields[k] = v
        return resp


# ──────────────────────────────────────────────
# gRPC SyncService implementation
# ──────────────────────────────────────────────

class SyncServiceServicer(sync_pb2_grpc.SyncServiceServicer):
    """Full implementation of the SyncService."""

    def __init__(self, store: Optional[EntityStore] = None):
        self.store = store or EntityStore()

    def SyncStream(self, request_iterator, context):
        """
        Bidirectional streaming handler.
        Each client sends a stream of SyncRequests (in Lamport clock order).
        Server responds per-transaction with resolution results.
        """
        client_id = ""
        try:
            for request in request_iterator:
                client_id = request.client_id or client_id

                # Log the incoming request
                logger.info(
                    "RECV  client=%s entity=%s txn=%s clock=%d op=%s",
                    request.client_id,
                    request.entity_id,
                    request.transaction_id,
                    request.clock.timestamp,
                    request.operation,
                )

                # Resolve (dedup + conflict detection + persistence)
                response, _ = self.store.resolve_conflict(request)

                logger.info(
                    "SEND  client=%s entity=%s txn=%s status=%s clock=%d msg='%s'",
                    request.client_id,
                    request.entity_id,
                    request.transaction_id,
                    SyncStatus.Name(response.status),
                    response.server_clock.timestamp,
                    response.message,
                )

                yield response

        except grpc.RpcError as e:
            logger.warning("Stream aborted for client %s: %s", client_id, e)
        except Exception as e:
            logger.error("Stream error for client %s: %s", client_id, e, exc_info=True)
            context.abort(grpc.StatusCode.INTERNAL, str(e))

    def GetClock(self, request, context):
        server_clock = self.store.get_current_clock()
        return sync_pb2.GetClockResponse(
            server_clock=common_pb2.LamportClock(
                timestamp=server_clock, node_id="server"
            )
        )

    def AcknowledgeSync(self, request, context):
        # In production, this would update the client's acked clock position
        return sync_pb2.AcknowledgeSyncResponse(
            accepted=True,
            message=f"Acknowledged txn={request.transaction_id} status={SyncStatus.Name(request.applied_status)}",
        )


# ──────────────────────────────────────────────
# Server lifecycle
# ──────────────────────────────────────────────

def create_server(
    host: str = "0.0.0.0",
    port: int = 50051,
    max_workers: int = 10,
    store: Optional[EntityStore] = None,
) -> grpc.Server:
    """Create and start a gRPC server with the SyncService."""
    server = grpc.server(
        futures.ThreadPoolExecutor(max_workers=max_workers),
        options=[
            ("grpc.max_send_message_length", 4 * 1024 * 1024),   # 4MB
            ("grpc.max_receive_message_length", 4 * 1024 * 1024),
        ],
    )
    servicer = SyncServiceServicer(store=store)
    sync_pb2_grpc.add_SyncServiceServicer_to_server(servicer, server)
    server.add_insecure_port(f"{host}:{port}")
    return server, servicer


# ──────────────────────────────────────────────
# Main
# ──────────────────────────────────────────────

if __name__ == "__main__":
    logging.basicConfig(
        level=logging.INFO,
        format="%(asctime)s [%(levelname)s] %(message)s",
    )
    server, _ = create_server()
    server.start()
    logger.info("TAUT Sync Server started on 0.0.0.0:50051")
    server.wait_for_termination()
