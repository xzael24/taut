package taut.sync.spike.client

import io.grpc.ManagedChannelBuilder
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.Channel.Factory.UNLIMITED
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import taut.sync.v1.*

/**
 * gRPC client that simulates Device A and Device B syncing with conflict scenarios.
 */
fun main() = runBlocking {
    println("============================================")
    println("  TAUT Sync Spike — gRPC Bidirectional Sync")
    println("============================================")
    println()

    val channel = ManagedChannelBuilder.forAddress("localhost", 50051)
        .usePlaintext()
        .build()

    val stub = TransactionSyncGrpc.newStub(channel)

    // Track Lamport clocks per device
    var deviceAClock = 0L
    var deviceBClock = 0L

    // =========================================================
    // Phase 1: Device A creates 3 transactions offline, then syncs
    // =========================================================
    println("--- PHASE 1: Device A creates 3 transactions offline ---")
    println()

    val deviceATx1 = Transaction.newBuilder()
        .setId("tx-a-001")
        .setTitle("Groceries")
        .setAmount(45.50)
        .setCreatedAt(System.currentTimeMillis())
        .setLamportTimestamp(++deviceAClock)
        .setDeviceId("device-a")
        .addItems(
            TransactionItem.newBuilder()
                .setId("item-1")
                .setDescription("Milk")
                .setAmount(3.50)
                .setQuantity(2)
        )
        .addItems(
            TransactionItem.newBuilder()
                .setId("item-2")
                .setDescription("Bread")
                .setAmount(2.50)
                .setQuantity(1)
        )
        .build()

    val deviceATx2 = Transaction.newBuilder()
        .setId("tx-a-002")
        .setTitle("Gas")
        .setAmount(55.00)
        .setCreatedAt(System.currentTimeMillis())
        .setLamportTimestamp(++deviceAClock)
        .setDeviceId("device-a")
        .build()

    // This is the CONFLICTING transaction — same ID as Device A's tx but different data
    val deviceATx3 = Transaction.newBuilder()
        .setId("tx-conflict-001")
        .setTitle("Device A: Office Supplies")
        .setAmount(120.00)
        .setCreatedAt(System.currentTimeMillis())
        .setLamportTimestamp(++deviceAClock)
        .setDeviceId("device-a")
        .build()

    println("[Device A] Local transactions ready:")
    println("  ${deviceATx1.id}: ${deviceATx1.title} (clock=${deviceATx1.lamportTimestamp})")
    println("  ${deviceATx2.id}: ${deviceATx2.title} (clock=${deviceATx2.lamportTimestamp})")
    println("  ${deviceATx3.id}: ${deviceATx3.title} (clock=${deviceATx3.lamportTimestamp})")
    println()

    // Device A syncs to server
    println("--- PHASE 2: Device A syncs to server ---")
    println()

    syncDevice(
        stub = stub,
        deviceId = "device-a",
        transactions = listOf(deviceATx1, deviceATx2, deviceATx3),
        lamportClock = deviceAClock
    )

    println()
    println("--- PHASE 3: Device B creates 2 transactions (1 conflicting) ---")
    println()

    // Device B creates transactions including one with a CONFLICTING id (same as tx-a-003)
    // but with a LOWER Lamport timestamp — this should be REJECTED (first-write-wins)
    val deviceBTx1 = Transaction.newBuilder()
        .setId("tx-b-001")
        .setTitle("Restaurant Dinner")
        .setAmount(85.00)
        .setCreatedAt(System.currentTimeMillis())
        .setLamportTimestamp(++deviceBClock)
        .setDeviceId("device-b")
        .build()

    // This CONFLICTS with deviceATx3 — same ID but Device B created it with lower clock
    // Device B was "offline" and started its clock from 0, so this will have clock=2
    val deviceBTx2 = Transaction.newBuilder()
        .setId("tx-conflict-001")
        .setTitle("Device B: Electronics")
        .setAmount(999.99)
        .setCreatedAt(System.currentTimeMillis())
        .setLamportTimestamp(++deviceBClock)
        .setDeviceId("device-b")
        .build()

    println("[Device B] Local transactions ready:")
    println("  ${deviceBTx1.id}: ${deviceBTx1.title} (clock=${deviceBTx1.lamportTimestamp})")
    println("  ${deviceBTx2.id}: ${deviceBTx2.title} (clock=${deviceBTx2.lamportTimestamp}) ⚠️ CONFLICTING with Device A's tx-conflict-001")
    println()

    // Device B syncs to server
    println("--- PHASE 4: Device B syncs to server (conflict expected) ---")
    println()

    syncDevice(
        stub = stub,
        deviceId = "device-b",
        transactions = listOf(deviceBTx1, deviceBTx2),
        lamportClock = deviceBClock
    )

    println()
    println("--- PHASE 5: Device A syncs again with HIGHER Lamport clock (LWW override) ---")
    println()

    // Now Device A comes back online, creates another conflicting transaction
    // with a HIGHER Lamport timestamp — this should be ACCEPTED via LWW
    val deviceATx4 = Transaction.newBuilder()
        .setId("tx-conflict-001")
        .setTitle("Device A: Office Supplies (UPDATED)")
        .setAmount(150.00)
        .setCreatedAt(System.currentTimeMillis())
        .setLamportTimestamp(++deviceAClock) // Higher clock = LWW wins
        .setDeviceId("device-a")
        .build()

    println("[Device A] Overwriting conflict with higher Lamport clock:")
    println("  ${deviceATx4.id}: ${deviceATx4.title} (clock=${deviceATx4.lamportTimestamp}) ⚡ LWW override")
    println()

    syncDevice(
        stub = stub,
        deviceId = "device-a",
        transactions = listOf(deviceATx4),
        lamportClock = deviceAClock
    )

    println()
    println("============================================")
    println("  SPIKE COMPLETE")
    println("============================================")

    channel.shutdown()
}

/**
 * Simulates a device syncing its transactions to the server.
 * Uses bidirectional streaming.
 */
private fun syncDevice(
    stub: TransactionSyncGrpc.TransactionSyncStub,
    deviceId: String,
    transactions: List<Transaction>,
    lamportClock: Long
): Unit = runBlocking {
    val responseChannel = Channel<SyncResponse>(UNLIMITED)

    val requestObserver = stub.syncTransactions(object : io.grpc.stub.StreamObserver<SyncResponse> {
        override fun onNext(response: SyncResponse) {
            println("[Server -> $deviceId] ACK=${response.serverAck}")
            println("[Server -> $deviceId] Server clock: ${response.serverState.lamportClock}")
            println("[Server -> $deviceId] Conflict resolution: ${response.conflictResolutionInstructions}")

            if (response.acceptedTransactionsCount > 0) {
                println("[Server -> $deviceId] Accepted transactions:")
                response.acceptedTransactionsList.forEach { tx ->
                    println("  ✅ ${tx.id}: ${tx.title} (device=${tx.deviceId})")
                }
            }

            if (response.rejectedTransactionsCount > 0) {
                println("[Server -> $deviceId] Rejected transactions:")
                response.rejectedTransactionsList.forEach { tx ->
                    println("  ❌ ${tx.id}: ${tx.title} (device=${tx.deviceId})")
                }
            }
            println()

            // Send response to our processing loop
            launch { responseChannel.send(response) }
        }

        override fun onError(t: Throwable) {
            System.err.println("[ERROR] $deviceId received error: ${t.message}")
            t.printStackTrace()
        }

        override fun onCompleted() {
            println("[Server -> $deviceId] Stream completed")
            responseChannel.close()
        }
    })

    // Send all transactions as a stream
    println("[Device $deviceId -> Server] Sending ${transactions.size} transactions (clock=$lamportClock)...")
    val metadata = SyncMetadata.newBuilder()
        .setLamportClock(lamportClock)
        .setDeviceId(deviceId)
        .setLastSyncAt(System.currentTimeMillis())
        .build()

    val request = SyncRequest.newBuilder()
        .setDeviceId(deviceId)
        .setMetadata(metadata)
        .addAllTransactions(transactions)
        .build()

    requestObserver.onNext(request)
    requestObserver.onCompleted()

    // Wait for all responses (consume channel)
    for (response in responseChannel) {
        // already handled in onNext
    }
}
