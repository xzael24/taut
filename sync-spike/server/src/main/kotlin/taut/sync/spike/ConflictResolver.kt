package taut.sync.spike

import taut.sync.v1.SyncMetadata
import taut.sync.v1.Transaction

/**
 * Conflict resolution strategy:
 * - Transactions: first-write-wins (the first version seen by the server wins)
 * - Metadata: LWW (last-writer-wins) based on Lamport clock
 */
class ConflictResolver(private val store: InMemoryStore) {

    data class ResolutionResult(
        val accepted: List<Transaction>,
        val rejected: List<Transaction>,
        val serverMetadata: SyncMetadata
    )

    fun resolve(
        deviceId: String,
        transactions: List<Transaction>,
        clientMetadata: SyncMetadata
    ): ResolutionResult {
        val accepted = mutableListOf<Transaction>()
        val rejected = mutableListOf<Transaction>()

        // Advance server Lamport clock based on client's clock
        val newClock = store.advanceLamportClock(clientMetadata.lamportClock)

        for (tx in transactions) {
            if (store.hasTransaction(tx.id)) {
                // First-write-wins: the existing transaction is kept
                val existing = store.getTransaction(tx.id)!!
                if (tx.lamportTimestamp > existing.lamportTimestamp) {
                    // LWW for metadata: if incoming has higher timestamp, it wins
                    println("[CONFLICT] Transaction ${tx.id} conflict detected: existing(device=${existing.deviceId}, clock=${existing.lamportTimestamp}) vs incoming(device=$deviceId, clock=${tx.lamportTimestamp})")
                    println("[CONFLICT] LWW rule: incoming wins (higher Lamport clock)")
                    store.putTransaction(tx)
                    accepted.add(tx)
                    println("[CONFLICT] Transaction ${tx.id} resolved via LWW (metadata update)")
                } else {
                    println("[CONFLICT] Transaction ${tx.id} conflict detected: existing(clock=${existing.lamportTimestamp}) >= incoming(clock=${tx.lamportTimestamp})")
                    println("[CONFLICT] First-write-wins: keeping existing")
                    rejected.add(tx)
                }
            } else {
                // New transaction, accept it
                store.putTransaction(tx)
                accepted.add(tx)
            }
        }

        val serverMetadata = SyncMetadata.newBuilder()
            .setLamportClock(newClock)
            .setDeviceId("server")
            .setLastSyncAt(System.currentTimeMillis() * 1000) // microseconds
            .build()

        return ResolutionResult(accepted, rejected, serverMetadata)
    }
}
