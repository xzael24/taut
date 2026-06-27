package taut.sync.spike

import taut.sync.v1.Transaction

/**
 * In-memory store simulating a database for the spike.
 * Thread-safe via synchronized.
 */
class InMemoryStore {
    private val transactions = mutableMapOf<String, Transaction>()
    private var lamportClock = 0L

    @Synchronized
    fun getTransaction(id: String): Transaction? = transactions[id]

    @Synchronized
    fun putTransaction(tx: Transaction) {
        transactions[tx.id] = tx
    }

    @Synchronized
    fun hasTransaction(id: String): Boolean = transactions.containsKey(id)

    @Synchronized
    fun getAllTransactions(): List<Transaction> = transactions.values.toList()

    @Synchronized
    fun getLamportClock(): Long = lamportClock

    @Synchronized
    fun advanceLamportClock(peerClock: Long): Long {
        lamportClock = maxOf(lamportClock, peerClock) + 1
        return lamportClock
    }

    @Synchronized
    fun updateLamportClock(value: Long) {
        lamportClock = maxOf(lamportClock, value)
    }
}
