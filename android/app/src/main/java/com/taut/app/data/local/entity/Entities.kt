package com.taut.app.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Local cache of waste categories — mirrors server's waste_categories table.
 *
 * Each category has a real photo (photo_path local) for operator recognition.
 * Price per unit is locked at transaction time.
 */
@Entity(
    tableName = "waste_categories",
    indices = [Index(value = ["code"], unique = true)]
)
data class WasteCategoryEntity(
    @PrimaryKey val id: String,  // UUIDv7
    @ColumnInfo(name = "code") val code: String,           // e.g., KRD-01
    @ColumnInfo(name = "name_id") val nameId: String,      // Bahasa Indonesia name
    @ColumnInfo(name = "name_en") val nameEn: String?,     // English name (optional)
    @ColumnInfo(name = "category_group") val categoryGroup: String, // kardus, plastik, etc.
    @ColumnInfo(name = "unit_type") val unitType: String = "kg",
    @ColumnInfo(name = "unit_price") val unitPrice: Long,  // Price in satuan rupiah (cents)
    @ColumnInfo(name = "photo_url") val photoUrl: String?, // Remote URL
    @ColumnInfo(name = "photo_path") val photoPath: String?, // Local file path (bundled)
    @ColumnInfo(name = "sort_order") val sortOrder: Int = 0,
    @ColumnInfo(name = "is_active") val isActive: Boolean = true,
    @ColumnInfo(name = "version") val version: Int = 0,   // For delta sync
    @ColumnInfo(name = "updated_at") val updatedAt: Long = System.currentTimeMillis()
)

/**
 * Local cache of registered customers (nasabah).
 * Mirrors users table where role = 'customer'.
 */
@Entity(
    tableName = "customers",
    indices = [Index(value = ["phone_number"], unique = true)]
)
data class CustomerEntity(
    @PrimaryKey val id: String,  // UUIDv7
    @ColumnInfo(name = "phone_number") val phoneNumber: String,
    @ColumnInfo(name = "name") val name: String,
    @ColumnInfo(name = "address") val address: String?,
    @ColumnInfo(name = "village_id") val villageId: Int?,
    @ColumnInfo(name = "is_active") val isActive: Boolean = true,
    @ColumnInfo(name = "last_visited_at") val lastVisitedAt: Long?,
    @ColumnInfo(name = "updated_at") val updatedAt: Long = System.currentTimeMillis()
)

/**
 * Local cache of operator profiles for kiosk mode.
 * Each device can have up to 5 operators.
 */
@Entity(
    tableName = "operator_profiles",
    foreignKeys = [
        ForeignKey(
            entity = UserEntity::class,
            parentColumns = ["id"],
            childColumns = ["user_id"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index(value = ["user_id"], unique = true)]
)
data class OperatorProfileEntity(
    @PrimaryKey val id: String,  // UUIDv7
    @ColumnInfo(name = "bank_sampah_id") val bankSampahId: String,
    @ColumnInfo(name = "user_id") val userId: String,
    @ColumnInfo(name = "pin_hash") val pinHash: String,    // bcrypt hash
    @ColumnInfo(name = "is_primary") val isPrimary: Boolean = false,
    @ColumnInfo(name = "is_active") val isActive: Boolean = true,
    @ColumnInfo(name = "created_at") val createdAt: Long = System.currentTimeMillis()
)

/**
 * Local user entity (operators + cached customers).
 * Mirrors server users table.
 */
@Entity(
    tableName = "users",
    indices = [Index(value = ["phone_number"], unique = true)]
)
data class UserEntity(
    @PrimaryKey val id: String,  // UUIDv7
    @ColumnInfo(name = "phone_number") val phoneNumber: String,
    @ColumnInfo(name = "role") val role: String,  // operator, customer
    @ColumnInfo(name = "name") val name: String?,
    @ColumnInfo(name = "kyc_status") val kycStatus: String = "unverified",
    @ColumnInfo(name = "is_active") val isActive: Boolean = true,
    @ColumnInfo(name = "updated_at") val updatedAt: Long = System.currentTimeMillis()
)

/**
 * Local transaction record — created offline, synced to server.
 *
 * Key design: UUIDv7 assigned on device before server sees it.
 * All monetary values in satuan rupiah (cents, BIGINT = Long).
 */
@Entity(
    tableName = "transactions",
    indices = [
        Index(value = ["status"]),
        Index(value = ["customer_id"]),
        Index(value = ["created_at"]),
        Index(value = ["sync_id"])
    ]
)
data class TransactionEntity(
    @PrimaryKey val id: String,  // UUIDv7
    @ColumnInfo(name = "bank_sampah_id") val bankSampahId: String,
    @ColumnInfo(name = "operator_id") val operatorId: String,
    @ColumnInfo(name = "customer_id") val customerId: String,
    @ColumnInfo(name = "transaction_type") val transactionType: String = "deposit",
    @ColumnInfo(name = "status") val status: String = "pending_sync", // pending_sync, synced, failed
    @ColumnInfo(name = "total_weight") val totalWeight: Long,  // in grams
    @ColumnInfo(name = "total_value") val totalValue: Long,    // in satuan rupiah
    @ColumnInfo(name = "device_timestamp") val deviceTimestamp: Long?,
    @ColumnInfo(name = "server_timestamp") val serverTimestamp: Long?,
    @ColumnInfo(name = "sync_id") val syncId: String?,
    @ColumnInfo(name = "is_offline_created") val isOfflineCreated: Boolean = true,
    @ColumnInfo(name = "hmac_signature") val hmacSignature: String?,
    @ColumnInfo(name = "price_snapshot") val priceSnapshot: String?,  // JSON string
    @ColumnInfo(name = "sms_sent") val smsSent: Boolean = false,
    @ColumnInfo(name = "created_at") val createdAt: Long = System.currentTimeMillis(),
    @ColumnInfo(name = "updated_at") val updatedAt: Long = System.currentTimeMillis()
)

/**
 * Line items within a transaction — one per waste category.
 * Weight in grams, price locked at transaction time.
 */
@Entity(
    tableName = "transaction_items",
    foreignKeys = [
        ForeignKey(
            entity = TransactionEntity::class,
            parentColumns = ["id"],
            childColumns = ["transaction_id"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index(value = ["transaction_id"]), Index(value = ["category_id"])]
)
data class TransactionItemEntity(
    @PrimaryKey val id: String,  // UUIDv7
    @ColumnInfo(name = "transaction_id") val transactionId: String,
    @ColumnInfo(name = "category_id") val categoryId: String,
    @ColumnInfo(name = "weight") val weight: Long,          // in grams
    @ColumnInfo(name = "price_per_unit") val pricePerUnit: Long,  // locked price in satuan
    @ColumnInfo(name = "total_value") val totalValue: Long,       // computed value
    @ColumnInfo(name = "created_at") val createdAt: Long = System.currentTimeMillis()
)

/**
 * SMS queue for offline-first SMS delivery.
 * Messages queued locally, sent when connectivity restored.
 */
@Entity(
    tableName = "sms_queue",
    indices = [Index(value = ["status"]), Index(value = ["transaction_id"])]
)
data class SmsQueueEntity(
    @PrimaryKey val id: String,  // UUIDv7
    @ColumnInfo(name = "transaction_id") val transactionId: String?,
    @ColumnInfo(name = "phone_to") val phoneTo: String,
    @ColumnInfo(name = "message") val message: String,
    @ColumnInfo(name = "status") val status: String = "pending", // pending, sent, failed
    @ColumnInfo(name = "retry_count") val retryCount: Int = 0,
    @ColumnInfo(name = "max_retries") val maxRetries: Int = 3,
    @ColumnInfo(name = "last_error") val lastError: String?,
    @ColumnInfo(name = "created_at") val createdAt: Long = System.currentTimeMillis()
)

/**
 * Device registration info for sync identity.
 */
@Entity(
    tableName = "devices",
    indices = [Index(value = ["device_phone_number"], unique = true)]
)
data class DeviceEntity(
    @PrimaryKey val id: String,  // UUIDv7
    @ColumnInfo(name = "bank_sampah_id") val bankSampahId: String,
    @ColumnInfo(name = "device_name") val deviceName: String?,
    @ColumnInfo(name = "device_phone_number") val devicePhoneNumber: String?,
    @ColumnInfo(name = "device_pub_key") val devicePubKey: String,
    @ColumnInfo(name = "app_version") val appVersion: String?,
    @ColumnInfo(name = "is_active") val isActive: Boolean = true,
    @ColumnInfo(name = "created_at") val createdAt: Long = System.currentTimeMillis()
)

/**
 * Sync log — tracks every device-server sync session.
 */
@Entity(
    tableName = "sync_log",
    indices = [Index(value = ["device_id"])]
)
data class SyncLogEntity(
    @PrimaryKey val id: String,  // UUIDv7
    @ColumnInfo(name = "device_id") val deviceId: String,
    @ColumnInfo(name = "status") val status: String = "in_progress",
    @ColumnInfo(name = "records_synced") val recordsSynced: Int = 0,
    @ColumnInfo(name = "records_failed") val recordsFailed: Int = 0,
    @ColumnInfo(name = "error_message") val errorMessage: String?,
    @ColumnInfo(name = "started_at") val startedAt: Long,
    @ColumnInfo(name = "completed_at") val completedAt: Long?,
    @ColumnInfo(name = "created_at") val createdAt: Long = System.currentTimeMillis()
)

/**
 * User consent log for UU PDP compliance (Pasal 20).
 */
@Entity(
    tableName = "user_consent_log",
    indices = [Index(value = ["user_id"])]
)
data class UserConsentLogEntity(
    @PrimaryKey val id: String,  // UUIDv7
    @ColumnInfo(name = "user_id") val userId: String,
    @ColumnInfo(name = "consent_type") val consentType: String,
    @ColumnInfo(name = "consent_version") val consentVersion: String,
    @ColumnInfo(name = "granted") val granted: Boolean,
    @ColumnInfo(name = "created_at") val createdAt: Long = System.currentTimeMillis()
)
