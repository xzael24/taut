package taut.sync.spike

import io.grpc.ServerBuilder

/**
 * gRPC server entry point for the sync spike.
 * Listens on port 50051.
 */
fun main() {
    val port = 50051
    val store = InMemoryStore()

    val server = ServerBuilder.forPort(port)
        .addService(SyncServiceImpl(store))
        .build()

    server.start()
    println("[SERVER] gRPC sync spike server started on port $port")
    println("[SERVER] Press Ctrl+C to stop")

    // Print current server state
    Runtime.getRuntime().addShutdownHook(Thread {
        println("[SERVER] Shutting down...")
        server.shutdown()
        println("[SERVER] Final store state:")
        store.getAllTransactions().forEach { tx ->
            println("  tx=${tx.id}, title=${tx.title}, amount=${tx.amount}, " +
                "device=${tx.deviceId}, lamport=${tx.lamportTimestamp}")
        }
    })

    // Block forever
    server.awaitTermination()
}
