package taut.sync.spike

import io.grpc.stub.StreamObserver
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.Channel.Factory.UNLIMITED
import kotlinx.coroutines.runBlocking
import taut.sync.v1.SyncRequest
import taut.sync.v1.SyncResponse
import taut.sync.v1.TransactionSyncGrpc

/**
 * Implementation of the TransactionSync gRPC service.
 * Handles bidirectional streaming with conflict resolution.
 */
class SyncServiceImpl(private val store: InMemoryStore) :
    TransactionSyncGrpc.TransactionSyncImplBase() {

    private val conflictResolver = ConflictResolver(store)

    override fun syncTransactions(
        responseObserver: StreamObserver<SyncResponse>
    ): StreamObserver<SyncRequest> {
        return object : StreamObserver<SyncRequest> {

            private val backChannel = Channel<SyncResponse>(UNLIMITED)

            override fun onNext(request: SyncRequest) {
                println("[SERVER] Received sync from device=${request.deviceId}, " +
                    "transactions=${request.transactionsCount}, " +
                    "lamport_clock=${request.metadata.lamportClock}")

                val result = conflictResolver.resolve(
                    deviceId = request.deviceId,
                    transactions = request.transactionsList,
                    clientMetadata = request.metadata
                )

                val instruction = if (result.rejected.isNotEmpty()) {
                    val rejectedIds = result.rejected.joinToString(", ") { it.id }
                    "CONFLICT: $rejectedIds were rejected (first-write-wins). " +
                        "Retry with higher Lamport timestamp to override via LWW."
                } else {
                    "ALL_ACCEPTED"
                }

                val response = SyncResponse.newBuilder()
                    .setServerAck(true)
                    .setConflictResolutionInstructions(instruction)
                    .setServerState(result.serverMetadata)
                    .addAllAcceptedTransactions(result.accepted)
                    .addAllRejectedTransactions(result.rejected)
                    .build()

                // Send response back via the stream
                runBlocking {
                    backChannel.send(response)
                }
            }

            override fun onError(t: Throwable) {
                System.err.println("[SERVER] Streaming error: ${t.message}")
            }

            override fun onCompleted() {
                println("[SERVER] Client finished streaming. Draining responses.")
                // Drain the channel and send all accumulated responses
                runBlocking {
                    for (response in backChannel) {
                        responseObserver.onNext(response)
                    }
                    responseObserver.onCompleted()
                }
            }
        }
    }
}
