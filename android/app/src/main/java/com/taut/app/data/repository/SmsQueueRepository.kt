package com.taut.app.data.repository

import com.taut.app.data.local.dao.SmsQueueDao
import com.taut.app.data.local.entity.SmsQueueEntity
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Repository for SMS queue operations.
 * Messages are queued locally and sent when connectivity is restored.
 */
@Singleton
class SmsQueueRepository @Inject constructor(
    private val dao: SmsQueueDao
) {
    /** Get pending SMS messages (oldest first). */
    suspend fun getPendingMessages(): List<SmsQueueEntity> =
        dao.getPendingMessages()

    /** Get all messages for a transaction. */
    suspend fun getMessagesForTransaction(transactionId: String): List<SmsQueueEntity> =
        dao.getMessagesForTransaction(transactionId)

    /** Queue a new SMS message. */
    suspend fun queueMessage(message: SmsQueueEntity) = dao.insert(message)

    /** Update message status after send attempt. */
    suspend fun updateStatus(id: String, status: String, error: String?) =
        dao.updateStatus(id, status, error)
}
