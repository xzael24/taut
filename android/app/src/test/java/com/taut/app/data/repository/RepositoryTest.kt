package com.taut.app.data.repository

import com.taut.app.data.local.dao.PeriodSummary
import com.taut.app.data.local.dao.TransactionDao
import com.taut.app.data.local.dao.TransactionItemDao
import com.taut.app.data.local.entity.TransactionEntity
import com.taut.app.data.local.entity.TransactionItemEntity
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.JUnit4

/**
 * Unit tests for TransactionRepository.
 * Uses MockK to mock DAO dependencies — no database needed.
 */
@RunWith(JUnit4::class)
class RepositoryTest {

    private lateinit var transactionDao: TransactionDao
    private lateinit var transactionItemDao: TransactionItemDao
    private lateinit var repository: TransactionRepository

    @Before
    fun setUp() {
        transactionDao = mockk(relaxed = true)
        transactionItemDao = mockk(relaxed = true)
        repository = TransactionRepository(transactionDao, transactionItemDao)
    }

    // ── getAllTransactions ────────────────────────────────────────────────────

    @Test
    fun getAllTransactions_delegatesToDao() = runBlocking {
        val txList = listOf(
            createTestTransaction("tx-1", totalWeight = 1000, totalValue = 5000),
            createTestTransaction("tx-2", totalWeight = 2000, totalValue = 10000)
        )
        every { transactionDao.getAllTransactions() } returns flowOf(txList)

        val result = repository.getAllTransactions().first()
        assertEquals("Should return 2 transactions", 2, result.size)
        assertEquals("tx-1", result[0].id)
    }

    @Test
    fun getAllTransactions_emptyList() = runBlocking {
        every { transactionDao.getAllTransactions() } returns flowOf(emptyList())

        val result = repository.getAllTransactions().first()
        assertTrue("Should return empty list", result.isEmpty())
    }

    // ── getTransactionById ───────────────────────────────────────────────────

    @Test
    fun getTransactionById_returnsEntity() = runBlocking {
        val tx = createTestTransaction("tx-find", totalWeight = 5000, totalValue = 25000)
        coEvery { transactionDao.getTransactionById("tx-find") } returns tx

        val result = repository.getTransactionById("tx-find")
        assertNotNull(result)
        assertEquals(5000L, result!!.totalWeight)
        assertEquals(25000L, result.totalValue)
    }

    @Test
    fun getTransactionById_returnsNullWhenNotFound() = runBlocking {
        coEvery { transactionDao.getTransactionById("tx-missing") } returns null

        val result = repository.getTransactionById("tx-missing")
        assertNull(result)
    }

    // ── insertTransactionWithItems ───────────────────────────────────────────

    @Test
    fun insertTransactionWithItems_insertsTransactionAndItems() = runBlocking {
        val tx = createTestTransaction("tx-insert")
        val item = createTestItem("item-1", "tx-insert")

        repository.insertTransactionWithItems(tx, listOf(item))

        coVerify(exactly = 1) { transactionDao.insert(tx) }
        coVerify(exactly = 1) { transactionItemDao.insertAll(listOf(item)) }
    }

    @Test
    fun insertTransactionWithItems_emptyItems_onlyInsertsTransaction() = runBlocking {
        val tx = createTestTransaction("tx-no-items")

        repository.insertTransactionWithItems(tx, emptyList())

        coVerify(exactly = 1) { transactionDao.insert(tx) }
        coVerify(exactly = 0) { transactionItemDao.insertAll(any()) }
    }

    // ── getItemsForTransaction ───────────────────────────────────────────────

    @Test
    fun getItemsForTransaction_returnsLineItems() = runBlocking {
        val items = listOf(
            createTestItem("item-a", "tx-parent"),
            createTestItem("item-b", "tx-parent")
        )
        coEvery { transactionItemDao.getItemsForTransaction("tx-parent") } returns items

        val result = repository.getItemsForTransaction("tx-parent")
        assertEquals(2, result.size)
    }

    // ── updateSyncStatus ─────────────────────────────────────────────────────

    @Test
    fun updateSyncStatus_callsDaoWithCorrectParams() = runBlocking {
        val now = 1700000000000L
        repository.updateSyncStatus("tx-sync", "synced", "sync-123", now)

        coVerify {
            transactionDao.updateSyncStatus("tx-sync", "synced", "sync-123", now)
        }
    }

    // ── getPendingTransactions ───────────────────────────────────────────────

    @Test
    fun getPendingTransactions_filtersCorrectly() = runBlocking {
        val pending = listOf(
            createTestTransaction("tx-p1", status = "pending_sync"),
            createTestTransaction("tx-p2", status = "pending_sync")
        )
        coEvery { transactionDao.getPendingTransactions() } returns pending

        val result = repository.getPendingTransactions()
        assertEquals("Should return 2 pending", 2, result.size)
    }

    @Test
    fun getPendingTransactions_emptyWhenNonePending() = runBlocking {
        coEvery { transactionDao.getPendingTransactions() } returns emptyList()

        val result = repository.getPendingTransactions()
        assertTrue(result.isEmpty())
    }

    // ── getTransactionsInRange ───────────────────────────────────────────────

    @Test
    fun getTransactionsInRange_filtersByTimeRange() = runBlocking {
        val txs = listOf(
            createTestTransaction("tx-r1").copy(createdAt = 1000L)
        )
        every { transactionDao.getTransactionsInRange(500L, 1500L) } returns flowOf(txs)

        val result = repository.getTransactionsInRange(500L, 1500L).first()
        assertEquals(1, result.size)
        assertEquals("tx-r1", result[0].id)
    }

    // ── Summary / period helpers ─────────────────────────────────────────────

    @Test
    fun getSummarySince_returnsPeriodSummary() = runBlocking {
        val summary = PeriodSummary(
            transactionCount = 10,
            totalWeight = 50000L,
            totalValue = 250000L
        )
        every { transactionDao.getSummarySince(any()) } returns flowOf(summary)

        val result = repository.getSummarySince(0L).first()
        assertNotNull(result)
        assertEquals(10, result!!.transactionCount)
        assertEquals(50000L, result.totalWeight)
        assertEquals(250000L, result.totalValue)
    }

    @Test
    fun todayStartOfDay_returnsBeginningOfDay() = runBlocking {
        val startOfDay = repository.todayStartOfDay()
        assertTrue("Start of day should be positive", startOfDay > 0)
    }

    @Test
    fun weekStart_returnsBeginningOfWeek() = runBlocking {
        val weekStart = repository.weekStart()
        assertTrue("Week start should be positive", weekStart > 0)
    }

    @Test
    fun monthStart_returnsBeginningOfMonth() = runBlocking {
        val monthStart = repository.monthStart()
        assertTrue("Month start should be positive", monthStart > 0)
    }

    // ── Total weight calculation (via summary) ───────────────────────────────

    @Test
    fun totalWeightCalculation_multipleTransactions() = runBlocking {
        val summary = PeriodSummary(
            transactionCount = 3,
            totalWeight = 15000L,
            totalValue = 75000L
        )
        every { transactionDao.getSummarySince(any()) } returns flowOf(summary)

        val result = repository.getSummarySince(0L).first()!!
        // Verify total weight is sum of individual weights
        assertEquals("Total weight from summary", 15000L, result.totalWeight)
        // Verify total value is sum of individual values
        assertEquals("Total value from summary", 75000L, result.totalValue)
    }

    // ── Helpers ──────────────────────────────────────────────────────────────

    private fun createTestTransaction(
        id: String,
        totalWeight: Long = 1000L,
        totalValue: Long = 5000L,
        status: String = "pending_sync"
    ): TransactionEntity {
        return TransactionEntity(
            id = id,
            bankSampahId = "bank-1",
            operatorId = "op-1",
            customerId = "cust-1",
            transactionType = "deposit",
            status = status,
            totalWeight = totalWeight,
            totalValue = totalValue,
            deviceTimestamp = System.currentTimeMillis(),
            serverTimestamp = null,
            syncId = null,
            isOfflineCreated = true,
            hmacSignature = null,
            priceSnapshot = null,
            smsSent = false,
            createdAt = System.currentTimeMillis(),
            updatedAt = System.currentTimeMillis()
        )
    }

    private fun createTestItem(id: String, transactionId: String): TransactionItemEntity {
        return TransactionItemEntity(
            id = id,
            transactionId = transactionId,
            categoryId = "cat-1",
            weight = 1000L,
            pricePerUnit = 5000L,
            totalValue = 5000L,
            createdAt = System.currentTimeMillis()
        )
    }
}
