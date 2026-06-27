package com.taut.app.data.repository

import com.taut.app.data.local.dao.PeriodSummary
import com.taut.app.data.local.dao.TransactionDao
import com.taut.app.data.local.dao.TransactionItemDao
import com.taut.app.data.local.entity.TransactionEntity
import com.taut.app.data.local.entity.TransactionItemEntity
import kotlinx.coroutines.flow.Flow
import java.util.Calendar
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Repository for transaction operations.
 * Provides transaction CRUD, history queries, and daily summaries.
 */
@Singleton
class TransactionRepository @Inject constructor(
    private val transactionDao: TransactionDao,
    private val transactionItemDao: TransactionItemDao
) {
    /** Observe all transactions, newest first. */
    fun getAllTransactions(): Flow<List<TransactionEntity>> = transactionDao.getAllTransactions()

    /** Observe transactions within a time range (inclusive). */
    fun getTransactionsInRange(startTime: Long, endTime: Long): Flow<List<TransactionEntity>> =
        transactionDao.getTransactionsInRange(startTime, endTime)

    /** Get a transaction by ID. */
    suspend fun getTransactionById(id: String): TransactionEntity? = transactionDao.getTransactionById(id)

    /** Get all line items for a transaction. */
    suspend fun getItemsForTransaction(transactionId: String): List<TransactionItemEntity> =
        transactionItemDao.getItemsForTransaction(transactionId)

    /** Insert a transaction with its line items atomically. */
    suspend fun insertTransactionWithItems(
        transaction: TransactionEntity,
        items: List<TransactionItemEntity>
    ) {
        transactionDao.insert(transaction)
        if (items.isNotEmpty()) {
            transactionItemDao.insertAll(items)
        }
    }

    /** Update sync status after server response. */
    suspend fun updateSyncStatus(id: String, status: String, syncId: String?, updatedAt: Long) {
        transactionDao.updateSyncStatus(id, status, syncId, updatedAt)
    }

    /** Get transactions pending sync. */
    suspend fun getPendingTransactions(): List<TransactionEntity> = transactionDao.getPendingTransactions()

    /** Observe aggregated summary since a given timestamp. */
    fun getSummarySince(startTime: Long): Flow<PeriodSummary?> = transactionDao.getSummarySince(startTime)

    /** Delete a transaction by ID. */
    suspend fun deleteTransactionById(id: String) = transactionDao.deleteById(id)

    /** Convenience: get today's start-of-day timestamp. */
    fun todayStartOfDay(): Long {
        val cal = Calendar.getInstance()
        cal.set(Calendar.HOUR_OF_DAY, 0)
        cal.set(Calendar.MINUTE, 0)
        cal.set(Calendar.SECOND, 0)
        cal.set(Calendar.MILLISECOND, 0)
        return cal.timeInMillis
    }

    /** Convenience: get start of this week (Monday). */
    fun weekStart(): Long {
        val cal = Calendar.getInstance()
        cal.firstDayOfWeek = Calendar.MONDAY
        cal.set(Calendar.DAY_OF_WEEK, Calendar.MONDAY)
        cal.set(Calendar.HOUR_OF_DAY, 0)
        cal.set(Calendar.MINUTE, 0)
        cal.set(Calendar.SECOND, 0)
        cal.set(Calendar.MILLISECOND, 0)
        return cal.timeInMillis
    }

    /** Convenience: get start of this month. */
    fun monthStart(): Long {
        val cal = Calendar.getInstance()
        cal.set(Calendar.DAY_OF_MONTH, 1)
        cal.set(Calendar.HOUR_OF_DAY, 0)
        cal.set(Calendar.MINUTE, 0)
        cal.set(Calendar.SECOND, 0)
        cal.set(Calendar.MILLISECOND, 0)
        return cal.timeInMillis
    }
}
