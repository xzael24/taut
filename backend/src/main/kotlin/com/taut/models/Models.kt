package com.taut.models

import kotlinx.serialization.Serializable

@Serializable
data class WasteBank(
    val id: String,                         // UUIDv7
    val code: String,                       // BSA-XXXX
    val name: String,
    val phone: String,
    val address: String? = null,
    val villageId: Int,
    val photoUrl: String? = null,
    val devicePubKey: String? = null,
    val isActive: Boolean = true,
    val createdAt: String,                  // ISO8601
    val updatedAt: String                   // ISO8601
)

@Serializable
data class User(
    val id: String,                         // UUIDv7
    val phoneNumber: String,
    val role: UserRole = UserRole.CUSTOMER,
    val name: String? = null,
    val villageId: Int? = null,
    val kycStatus: String = "unverified",
    val pinHash: String? = null,
    val pinSalt: String? = null,
    val failedPinAttempts: Int = 0,
    val isActive: Boolean = true,
    val createdAt: String,
    val updatedAt: String
)

@Serializable
enum class UserRole {
    OPERATOR, CUSTOMER, DLH_STAFF, ADMIN, SUPERADMIN
}

@Serializable
data class OperatorProfile(
    val id: String,
    val bankSampahId: String,
    val userId: String,
    val pinHash: String? = null,
    val isPrimary: Boolean = false,
    val isActive: Boolean = true,
    val createdAt: String
)

@Serializable
data class WasteCategory(
    val id: String,
    val code: String,                       // KRD-01, PLT-01
    val nameId: String,
    val nameEn: String? = null,
    val categoryGroup: String,              // kardus, plastik, kaca, logam, kertas, elektronik, lainnya
    val unitType: String = "kg",
    val unitPrice: Long = 0,               // In satuan rupiah (cents)
    val photoUrl: String? = null,
    val sortOrder: Int = 0,
    val isActive: Boolean = true,
    val createdAt: String,
    val updatedAt: String
)

@Serializable
enum class TransactionType {
    DEPOSIT, REDEMPTION
}

@Serializable
enum class TransactionStatus {
    PENDING_SYNC, SYNCING, SYNCED, CONFIRMED, FAILED, FAILED_MANUAL, VOIDED
}

@Serializable
data class Transaction(
    val id: String,
    val bankSampahId: String,
    val operatorId: String,
    val customerId: String,
    val transactionType: TransactionType = TransactionType.DEPOSIT,
    val status: TransactionStatus = TransactionStatus.PENDING_SYNC,
    val totalWeight: Long,                  // In grams
    val totalValue: Long,                   // In satuan rupiah
    val deviceTimestamp: String? = null,
    val serverTimestamp: String? = null,
    val syncId: String? = null,
    val isOfflineCreated: Boolean = true,
    val lamportTimestamp: Long? = null,
    val hmacSignature: String? = null,
    val priceSnapshot: String? = null,
    val weightPhotoUrl: String? = null,
    val fraudFlag: Boolean = false,
    val fraudReason: String? = null,
    val smsSent: Boolean = false,
    val smsSentAt: String? = null,
    val items: List<TransactionItem> = emptyList(),
    val createdAt: String,
    val updatedAt: String
)

@Serializable
data class TransactionItem(
    val id: String,
    val transactionId: String,
    val categoryId: String,
    val weight: Long,                       // In grams
    val pricePerUnit: Long,                 // Locked at transaction time
    val totalValue: Long,                   // (weight / 1000) * pricePerUnit
    val createdAt: String
)

@Serializable
data class PriceReference(
    val id: String,
    val categoryId: String,
    val regionId: Int,
    val regionType: String = "national",    // national, kota, kecamatan
    val source: String,                     // admin, pengepul, system
    val pricePerUnit: Long,
    val effectiveFrom: String,
    val effectiveTo: String? = null,
    val version: Int,
    val isCurrent: Boolean = true,
    val createdAt: String
)

@Serializable
data class Device(
    val id: String,
    val bankSampahId: String,
    val deviceName: String? = null,
    val devicePhoneNumber: String? = null,
    val devicePubKey: String,
    val deviceFingerprint: String? = null,
    val registeredAt: String,
    val lastSeenAt: String? = null,
    val isActive: Boolean = true,
    val appVersion: String? = null,
    val createdAt: String,
    val updatedAt: String
)

@Serializable
data class SyncLog(
    val id: String,
    val deviceId: String,
    val status: String,                     // in_progress, completed, partial, failed
    val lastSyncCursor: String? = null,
    val lamportTimestamp: Long? = null,
    val recordsSynced: Int = 0,
    val recordsFailed: Int = 0,
    val startedAt: String,
    val completedAt: String? = null,
    val errorMessage: String? = null,
    val createdAt: String
)

@Serializable
data class Village(
    val id: Int,
    val kecamatanId: Int,
    val nameId: String,
    val postalCode: String? = null,
    val createdAt: String
)

@Serializable
data class Kecamatan(
    val id: Int,
    val kotaId: Int,
    val nameId: String,
    val createdAt: String
)

@Serializable
data class Kota(
    val id: Int,
    val provinsiId: Int,
    val nameId: String
)

@Serializable
data class Provinsi(
    val id: Int,
    val nameId: String,
    val isoCode: String? = null
)

// ── Financial / Ledger models ──

@Serializable
data class LedgerAccount(
    val id: String,
    val accountCode: String,
    val nameId: String,
    val accountType: String,                // asset, liability, equity, revenue, expense
    val normalBalance: String,              // debit, credit
    val isActive: Boolean = true,
    val createdAt: String
)

@Serializable
data class LedgerEntry(
    val id: String,
    val accountId: String,
    val entryType: String,                  // debit, credit
    val amount: Long,                       // Always positive
    val balanceAfter: Long,
    val referenceType: String,
    val referenceId: String,
    val reasonCode: String,
    val actorId: String? = null,
    val description: String? = null,
    val createdAt: String
)

// ── SMS model ──

@Serializable
data class SmsMessage(
    val id: String,
    val transactionId: String? = null,
    val phoneTo: String,
    val message: String,
    val status: String = "pending",         // pending, sending, sent, failed, bounced, cancelled
    val provider: String? = null,
    val providerMessageId: String? = null,
    val cost: Long? = null,
    val retryCount: Int = 0,
    val maxRetries: Int = 3,
    val lastError: String? = null,
    val sentAt: String? = null,
    val createdAt: String
)

// ── Compliance models ──

@Serializable
data class UserConsentLog(
    val id: String,
    val userId: String,
    val consentType: String,
    val consentVersion: String,
    val granted: Boolean,
    val ipAddress: String? = null,
    val deviceId: String? = null,
    val createdAt: String
)
