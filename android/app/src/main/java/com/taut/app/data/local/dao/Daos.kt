package com.taut.app.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.taut.app.data.local.entity.CustomerEntity
import com.taut.app.data.local.entity.DeviceEntity
import com.taut.app.data.local.entity.OperatorProfileEntity
import com.taut.app.data.local.entity.SmsQueueEntity
import com.taut.app.data.local.entity.SyncLogEntity
import com.taut.app.data.local.entity.TransactionEntity
import com.taut.app.data.local.entity.TransactionItemEntity
import com.taut.app.data.local.entity.UserEntity
import com.taut.app.data.local.entity.WasteCategoryEntity
import kotlinx.coroutines.flow.Flow

/**
 * Escape SQL LIKE wildcards (% and _) for safe use in LIKE queries.
 * Must be used together with ESCAPE '\\' clause in the SQL query.
 */
internal fun escapeForLike(value: String): String = value
    .replace("\\", "\\\\")  // Escape backslash first
    .replace("%", "\\%")
    .replace("_", "\\_")

/**
 * DAO for waste categories — cached locally for offline access.
 */
@Dao
interface WasteCategoryDao {
    @Query("SELECT * FROM waste_categories WHERE is_active = 1 ORDER BY sort_order ASC")
    fun getAllCategories(): Flow<List<WasteCategoryEntity>>

    @Query("SELECT * FROM waste_categories WHERE id = :id")
    suspend fun getCategoryById(id: String): WasteCategoryEntity?

    @Query("SELECT * FROM waste_categories WHERE name_id LIKE '%' || :query || '%' ESCAPE '\\'")
    fun searchCategories(query: String): Flow<List<WasteCategoryEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(categories: List<WasteCategoryEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(category: WasteCategoryEntity)

    @Update
    suspend fun update(category: WasteCategoryEntity)

    @Query("DELETE FROM waste_categories WHERE id = :id")
    suspend fun deleteById(id: String)

    @Query("DELETE FROM waste_categories")
    suspend fun deleteAll()
}

/**
 * DAO for customers (nasabah) — stored locally for offline lookup.
 */
@Dao
interface CustomerDao {
    @Query("SELECT * FROM customers WHERE is_active = 1 ORDER BY name ASC")
    fun getAllCustomers(): Flow<List<CustomerEntity>>

    @Query("SELECT * FROM customers WHERE id = :id")
    suspend fun getCustomerById(id: String): CustomerEntity?

    @Query("SELECT * FROM customers WHERE phone_number = :phone")
    suspend fun getCustomerByPhone(phone: String): CustomerEntity?

    @Query("SELECT * FROM customers WHERE name LIKE '%' || :query || '%' ESCAPE '\\' OR phone_number LIKE '%' || :query || '%' ESCAPE '\\'")
    fun searchCustomers(query: String): Flow<List<CustomerEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(customer: CustomerEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(customers: List<CustomerEntity>)

    @Update
    suspend fun update(customer: CustomerEntity)

    @Query("DELETE FROM customers WHERE id = :id")
    suspend fun deleteById(id: String)
}

/**
 * Aggregate summary for a period — used on HomeScreen and HistoryScreen.
 */
data class PeriodSummary(
    val transactionCount: Int = 0,
    val totalWeight: Long = 0L,
    val totalValue: Long = 0L
)

/**
 * DAO for transactions — the core business record.
 */
@Dao
interface TransactionDao {
    @Query("SELECT * FROM transactions ORDER BY created_at DESC")
    fun getAllTransactions(): Flow<List<TransactionEntity>>

    @Query("SELECT * FROM transactions WHERE status = 'pending_sync' ORDER BY created_at ASC")
    suspend fun getPendingTransactions(): List<TransactionEntity>

    @Query("SELECT * FROM transactions WHERE id = :id")
    suspend fun getTransactionById(id: String): TransactionEntity?

    @Query("""
        SELECT * FROM transactions 
        WHERE created_at >= :startTime AND created_at <= :endTime 
        ORDER BY created_at DESC
    """)
    fun getTransactionsInRange(startTime: Long, endTime: Long): Flow<List<TransactionEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(transaction: TransactionEntity)

    @Update
    suspend fun update(transaction: TransactionEntity)

    @Query("UPDATE transactions SET status = :status, sync_id = :syncId, updated_at = :updatedAt WHERE id = :id")
    suspend fun updateSyncStatus(id: String, status: String, syncId: String?, updatedAt: Long)

    @Query("""
        SELECT COUNT(*) AS transactionCount, 
               COALESCE(SUM(total_weight), 0) AS totalWeight,
               COALESCE(SUM(total_value), 0) AS totalValue
        FROM transactions WHERE created_at >= :startTime
    """)
    fun getSummarySince(startTime: Long): Flow<PeriodSummary?>

    @Query("SELECT COUNT(*) FROM transactions WHERE created_at >= :since AND status = 'pending_sync'")
    suspend fun countPendingTransactions(since: Long): Int

    @Query("DELETE FROM transactions WHERE id = :id")
    suspend fun deleteById(id: String)
}

/**
 * DAO for transaction items (line items within a transaction).
 */
@Dao
interface TransactionItemDao {
    @Query("SELECT * FROM transaction_items")
    fun getAllItems(): Flow<List<TransactionItemEntity>>

    @Query("SELECT * FROM transaction_items WHERE id = :id")
    suspend fun getItemById(id: String): TransactionItemEntity?

    @Query("SELECT * FROM transaction_items WHERE transaction_id = :transactionId")
    suspend fun getItemsForTransaction(transactionId: String): List<TransactionItemEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(items: List<TransactionItemEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(item: TransactionItemEntity)

    @Update
    suspend fun update(item: TransactionItemEntity)

    @Query("DELETE FROM transaction_items WHERE id = :id")
    suspend fun deleteById(id: String)

    @Query("DELETE FROM transaction_items WHERE transaction_id = :transactionId")
    suspend fun deleteByTransactionId(transactionId: String)
}
/**
 * DAO for user entities (operators and cached customers).
 * Mirrors server users table.
 */
@Dao
interface UserDao {
    @Query("SELECT * FROM users")
    fun getAllUsers(): Flow<List<UserEntity>>

    @Query("SELECT * FROM users WHERE phone_number = :phone")
    suspend fun getUserByPhone(phone: String): UserEntity?

    @Query("SELECT * FROM users WHERE id = :id")
    suspend fun getUserById(id: String): UserEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(user: UserEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(users: List<UserEntity>)

    @Update
    suspend fun update(user: UserEntity)

    @Query("DELETE FROM users WHERE id = :id")
    suspend fun deleteById(id: String)

    @Query("DELETE FROM users")
    suspend fun deleteAll()
}

/**
 * DAO for operator profiles (PIN-authenticated local profiles).
 */
@Dao
interface OperatorProfileDao {
    @Query("SELECT * FROM operator_profiles")
    fun getAllProfiles(): Flow<List<OperatorProfileEntity>>

    @Query("SELECT * FROM operator_profiles WHERE is_active = 1")
    suspend fun getActiveProfiles(): List<OperatorProfileEntity>

    @Query("SELECT * FROM operator_profiles WHERE id = :id")
    suspend fun getProfileById(id: String): OperatorProfileEntity?

    @Query("SELECT * FROM operator_profiles WHERE user_id = :userId")
    suspend fun getProfileByUserId(userId: String): OperatorProfileEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(profile: OperatorProfileEntity)

    @Update
    suspend fun update(profile: OperatorProfileEntity)

    @Query("DELETE FROM operator_profiles WHERE id = :id")
    suspend fun deleteById(id: String)

    @Query("DELETE FROM operator_profiles")
    suspend fun deleteAll()
}

/**
 * DAO for SMS queue — tracks pending/sent/failed messages.
 */
@Dao
interface SmsQueueDao {
    @Query("SELECT * FROM sms_queue ORDER BY created_at ASC")
    fun getAllMessages(): Flow<List<SmsQueueEntity>>

    @Query("SELECT * FROM sms_queue WHERE id = :id")
    suspend fun getMessageById(id: String): SmsQueueEntity?

    @Query("SELECT * FROM sms_queue WHERE status = 'pending' ORDER BY created_at ASC")
    suspend fun getPendingMessages(): List<SmsQueueEntity>

    @Query("SELECT * FROM sms_queue WHERE transaction_id = :transactionId")
    suspend fun getMessagesForTransaction(transactionId: String): List<SmsQueueEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(message: SmsQueueEntity)

    @Update
    suspend fun update(message: SmsQueueEntity)

    @Query("UPDATE sms_queue SET status = :status, retry_count = retry_count + 1, last_error = :error WHERE id = :id")
    suspend fun updateStatus(id: String, status: String, error: String?)

    @Query("DELETE FROM sms_queue WHERE id = :id")
    suspend fun deleteById(id: String)

    @Query("DELETE FROM sms_queue")
    suspend fun deleteAll()
}
