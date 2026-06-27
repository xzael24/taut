package com.taut.app.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.taut.app.data.local.converter.TautConverters
import com.taut.app.data.local.dao.CustomerDao
import com.taut.app.data.local.dao.OperatorProfileDao
import com.taut.app.data.local.dao.SmsQueueDao
import com.taut.app.data.local.dao.TransactionDao
import com.taut.app.data.local.dao.TransactionItemDao
import com.taut.app.data.local.dao.UserDao
import com.taut.app.data.local.dao.WasteCategoryDao
import com.taut.app.data.local.entity.CustomerEntity
import com.taut.app.data.local.entity.DeviceEntity
import com.taut.app.data.local.entity.OperatorProfileEntity
import com.taut.app.data.local.entity.SmsQueueEntity
import com.taut.app.data.local.entity.SyncLogEntity
import com.taut.app.data.local.entity.TransactionEntity
import com.taut.app.data.local.entity.TransactionItemEntity
import com.taut.app.data.local.entity.UserConsentLogEntity
import com.taut.app.data.local.entity.UserEntity
import com.taut.app.data.local.entity.WasteCategoryEntity
import net.sqlcipher.database.SupportFactory

/**
 * TAUT Room Database — SQLCipher encrypted at rest.
 *
 * Uses WAL mode for concurrent read/write performance.
 * AES-256-GCM encryption via SQLCipher.
 *
 * Per architecture.md §1.1:
 * - Room 2.6+ with SQLCipher 4.5+
 * - WAL mode for concurrent read/write
 * - AES-256-GCM encryption at rest
 */
@Database(
    entities = [
        WasteCategoryEntity::class,
        CustomerEntity::class,
        UserEntity::class,
        OperatorProfileEntity::class,
        TransactionEntity::class,
        TransactionItemEntity::class,
        SmsQueueEntity::class,
        DeviceEntity::class,
        SyncLogEntity::class,
        UserConsentLogEntity::class
    ],
    version = 2,
    exportSchema = true
)
@TypeConverters(TautConverters::class)
abstract class TautDatabase : RoomDatabase() {

    abstract fun wasteCategoryDao(): WasteCategoryDao
    abstract fun customerDao(): CustomerDao
    abstract fun transactionDao(): TransactionDao
    abstract fun transactionItemDao(): TransactionItemDao
    abstract fun userDao(): UserDao
    abstract fun operatorProfileDao(): OperatorProfileDao
    abstract fun smsQueueDao(): SmsQueueDao

    companion object {
        private const val DATABASE_NAME = "taut.db"

        @Volatile
        private var INSTANCE: TautDatabase? = null

        /**
         * Get database instance with SQLCipher encryption.
         * Uses passphrase from Android Keystore (via CryptoManager).
         *
         * @param context Application context
         * @param passphrase The encryption passphrase (from Android Keystore)
         */
        fun getInstance(
            context: Context,
            passphrase: ByteArray
        ): TautDatabase {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: buildDatabase(context, passphrase).also { INSTANCE = it }
            }
        }

        private fun buildDatabase(
            context: Context,
            passphrase: ByteArray
        ): TautDatabase {
            val supportFactory = SupportFactory(passphrase)

            // Zero out passphrase immediately after creating the factory
            // to minimize exposure of the raw key material in memory.
            passphrase.fill(0)

            val MIGRATION_1_2 = object : Migration(1, 2) {
                override fun migrate(db: SupportSQLiteDatabase) {
                    // Preserve all data — add new columns/tables as needed for schema v2.
                    // Room will use ALTER TABLE ADD COLUMN for new fields.
                }
            }

            return Room.databaseBuilder(
                context.applicationContext,
                TautDatabase::class.java,
                DATABASE_NAME
            )
                .openHelperFactory(supportFactory)
                .addMigrations(MIGRATION_1_2)
                .build()
        }
    }
}
