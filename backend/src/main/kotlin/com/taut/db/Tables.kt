package com.taut.db

import org.jetbrains.exposed.sql.Table
import org.jetbrains.exposed.sql.javatime.datetime
import org.jetbrains.exposed.sql.javatime.date

// ── Waste Banks ──
object WasteBanks : Table("waste_banks") {
    val id = uuid("id")
    val code = varchar("code", 20).uniqueIndex()
    val name = varchar("name", 200)
    val phone = varchar("phone", 20)
    val address = varchar("address", 500).nullable()
    val villageId = integer("village_id")
    val photoUrl = varchar("photo_url", 500).nullable()
    val devicePubKey = text("device_pub_key").nullable()
    val isActive = bool("is_active").default(true)
    val createdAt = datetime("created_at")
    val updatedAt = datetime("updated_at")

    override val primaryKey = PrimaryKey(id)
}

// ── Users ──
object Users : Table("users") {
    val id = uuid("id")
    val phoneNumber = varchar("phone_number", 20).uniqueIndex()
    val role = varchar("role", 20).default("CUSTOMER")
    val name = varchar("name", 200).nullable()
    val villageId = integer("village_id").nullable()
    val kycStatus = varchar("kyc_status", 20).default("unverified")
    val pinHash = varchar("pin_hash", 200).nullable()
    val pinSalt = varchar("pin_salt", 100).nullable()
    val failedPinAttempts = integer("failed_pin_attempts").default(0)
    val isActive = bool("is_active").default(true)
    val createdAt = datetime("created_at")
    val updatedAt = datetime("updated_at")

    override val primaryKey = PrimaryKey(id)
}

// ── Operator Profiles ──
object OperatorProfiles : Table("operator_profiles") {
    val id = uuid("id")
    val bankSampahId = uuid("bank_sampah_id").references(WasteBanks.id)
    val userId = uuid("user_id").references(Users.id)
    val pinHash = varchar("pin_hash", 200).nullable()
    val isPrimary = bool("is_primary").default(false)
    val isActive = bool("is_active").default(true)
    val createdAt = datetime("created_at")

    override val primaryKey = PrimaryKey(id)
}

// ── Waste Categories ──
object WasteCategories : Table("waste_categories") {
    val id = uuid("id")
    val code = varchar("code", 20).uniqueIndex()
    val nameId = varchar("name_id", 200)
    val nameEn = varchar("name_en", 200).nullable()
    val categoryGroup = varchar("category_group", 50)
    val unitType = varchar("unit_type", 10).default("kg")
    val unitPrice = long("unit_price").default(0)
    val photoUrl = varchar("photo_url", 500).nullable()
    val sortOrder = integer("sort_order").default(0)
    val isActive = bool("is_active").default(true)
    val createdAt = datetime("created_at")
    val updatedAt = datetime("updated_at")

    override val primaryKey = PrimaryKey(id)
}

// ── Price References ──
object PriceReferences : Table("price_references") {
    val id = uuid("id")
    val categoryId = uuid("category_id").references(WasteCategories.id)
    val regionId = integer("region_id")
    val regionType = varchar("region_type", 20).default("national")
    val priceSource = varchar("price_source", 30)     // renamed from 'source' to avoid conflict with supertype
    val pricePerUnit = long("price_per_unit")
    val effectiveFrom = datetime("effective_from")
    val effectiveTo = datetime("effective_to").nullable()
    val version = integer("version")
    val isCurrent = bool("is_current").default(true)
    val createdAt = datetime("created_at")

    override val primaryKey = PrimaryKey(id)
}

// ── Transactions ──
object Transactions : Table("transactions") {
    val id = uuid("id")
    val bankSampahId = uuid("bank_sampah_id").references(WasteBanks.id)
    val operatorId = uuid("operator_id").references(Users.id)
    val customerId = uuid("customer_id").references(Users.id)
    val transactionType = varchar("transaction_type", 20).default("DEPOSIT")
    val status = varchar("status", 30).default("PENDING_SYNC")
    val totalWeight = long("total_weight")       // grams
    val totalValue = long("total_value")         // satuan rupiah
    val deviceTimestamp = datetime("device_timestamp").nullable()
    val serverTimestamp = datetime("server_timestamp").nullable()
    val syncId = varchar("sync_id", 100).nullable()
    val isOfflineCreated = bool("is_offline_created").default(true)
    val lamportTimestamp = long("lamport_timestamp").nullable()
    val hmacSignature = varchar("hmac_signature", 256).nullable()
    val priceSnapshot = text("price_snapshot").nullable()
    val weightPhotoUrl = varchar("weight_photo_url", 500).nullable()
    val fraudFlag = bool("fraud_flag").default(false)
    val fraudReason = varchar("fraud_reason", 500).nullable()
    val smsSent = bool("sms_sent").default(false)
    val smsSentAt = datetime("sms_sent_at").nullable()
    val voidedBy = uuid("voided_by").nullable()
    val voidReason = varchar("void_reason", 500).nullable()
    val voidedAt = datetime("voided_at").nullable()
    val createdAt = datetime("created_at")
    val updatedAt = datetime("updated_at")

    override val primaryKey = PrimaryKey(id)
}

// ── Transaction Items ──
object TransactionItems : Table("transaction_items") {
    val id = uuid("id")
    val transactionId = uuid("transaction_id").references(Transactions.id)
    val categoryId = uuid("category_id").references(WasteCategories.id)
    val weight = long("weight")                    // grams
    val pricePerUnit = long("price_per_unit")      // locked at tx time
    val totalValue = long("total_value")            // (weight/1000) * price_per_unit
    val createdAt = datetime("created_at")

    override val primaryKey = PrimaryKey(id)
}

// ── Devices ──
object Devices : Table("devices") {
    val id = uuid("id")
    val bankSampahId = uuid("bank_sampah_id").references(WasteBanks.id)
    val deviceName = varchar("device_name", 200).nullable()
    val devicePhoneNumber = varchar("device_phone_number", 20).nullable()
    val devicePubKey = text("device_pub_key")
    val deviceFingerprint = varchar("device_fingerprint", 100).nullable()
    val registeredAt = datetime("registered_at")
    val lastSeenAt = datetime("last_seen_at").nullable()
    val isActive = bool("is_active").default(true)
    val appVersion = varchar("app_version", 30).nullable()
    val isWiped = bool("is_wiped").default(false)
    val wipedAt = datetime("wiped_at").nullable()
    val createdAt = datetime("created_at")
    val updatedAt = datetime("updated_at")

    override val primaryKey = PrimaryKey(id)
}

// ── Sync Logs ──
object SyncLogs : Table("sync_logs") {
    val id = uuid("id")
    val deviceId = uuid("device_id").references(Devices.id)
    val syncStatus = varchar("sync_status", 30)    // renamed from 'status'
    val lastSyncCursor = varchar("last_sync_cursor", 100).nullable()
    val lamportTimestamp = long("lamport_timestamp").nullable()
    val recordsSynced = integer("records_synced").default(0)
    val recordsFailed = integer("records_failed").default(0)
    val startedAt = datetime("started_at")
    val completedAt = datetime("completed_at").nullable()
    val errorMessage = text("error_message").nullable()
    val createdAt = datetime("created_at")

    override val primaryKey = PrimaryKey(id)
}

// ── Ledger Accounts ──
object LedgerAccounts : Table("ledger_accounts") {
    val id = uuid("id")
    val accountCode = varchar("account_code", 20).uniqueIndex()
    val nameId = varchar("name_id", 200)
    val accountType = varchar("account_type", 20)
    val normalBalance = varchar("normal_balance", 10)
    val isActive = bool("is_active").default(true)
    val createdAt = datetime("created_at")

    override val primaryKey = PrimaryKey(id)
}

// ── Ledger Entries ──
object LedgerEntries : Table("ledger_entries") {
    val id = uuid("id")
    val accountId = uuid("account_id").references(LedgerAccounts.id)
    val entryType = varchar("entry_type", 10)   // debit, credit
    val amount = long("amount")
    val balanceAfter = long("balance_after")
    val referenceType = varchar("reference_type", 50)
    val referenceId = uuid("reference_id")
    val reasonCode = varchar("reason_code", 50)
    val actorId = uuid("actor_id").nullable()
    val description = varchar("description", 500).nullable()
    val createdAt = datetime("created_at")

    override val primaryKey = PrimaryKey(id)
}

// ── SMS Queue ──
object SmsQueue : Table("sms_queue") {
    val id = uuid("id")
    val transactionId = uuid("transaction_id").nullable()
    val phoneTo = varchar("phone_to", 20)
    val message = text("message")
    val smsStatus = varchar("sms_status", 20).default("pending")   // renamed from 'status'
    val provider = varchar("provider", 30).nullable()
    val providerMessageId = varchar("provider_message_id", 100).nullable()
    val cost = long("cost").nullable()
    val retryCount = integer("retry_count").default(0)
    val maxRetries = integer("max_retries").default(3)
    val lastError = text("last_error").nullable()
    val sentAt = datetime("sent_at").nullable()
    val createdAt = datetime("created_at")

    override val primaryKey = PrimaryKey(id)
}

// ── User Consent Log ──
object UserConsentLog : Table("user_consent_log") {
    val id = uuid("id")
    val userId = uuid("user_id").references(Users.id)
    val consentType = varchar("consent_type", 50)
    val consentVersion = varchar("consent_version", 20)
    val granted = bool("granted")
    val ipAddress = varchar("ip_address", 45).nullable()
    val deviceIdSql = varchar("device_id_sql", 100).nullable()    // renamed from 'deviceId'
    val createdAt = datetime("created_at")

    override val primaryKey = PrimaryKey(id)
}

// ── OTP Sessions (in lieu of Redis) ──
object OtpSessions : Table("otp_sessions") {
    val id = uuid("id")
    val phoneNumber = varchar("phone_number", 20)
    val otpHash = varchar("otp_hash", 200)
    val deviceId = varchar("device_id", 100).nullable()
    val bankSampahId = uuid("bank_sampah_id").nullable()
    val expiresAt = datetime("expires_at")
    val verified = bool("verified").default(false)
    val createdAt = datetime("created_at")

    override val primaryKey = PrimaryKey(id)
}

// ── Rate Limits (phone-based) ──
// phone_number is the natural PK. Scoped via prefix: plain phone = request limit,
// "v:" prefix = verify limit. Uses pg_advisory_xact_lock for serialization.
object RateLimits : Table("rate_limits") {
    val phoneNumber = varchar("phone_number", 20)
    val requestCount = integer("request_count").default(0)
    val windowStart = datetime("window_start")
    val expiresAt = datetime("expires_at")
    val updatedAt = datetime("updated_at")

    override val primaryKey = PrimaryKey(phoneNumber)
}

// ── Device Rate Limits ──
// device_id is the natural PK. Same design as RateLimits but for devices.
object DeviceRateLimits : Table("device_rate_limits") {
    val deviceId = varchar("device_id", 100)
    val requestCount = integer("request_count").default(0)
    val windowStart = datetime("window_start")
    val expiresAt = datetime("expires_at")
    val updatedAt = datetime("updated_at")

    override val primaryKey = PrimaryKey(deviceId)
}

// ── Processed IDs (idempotency) ──
object ProcessedIds : Table("processed_ids") {
    val id = uuid("id")
    val idempotencyKey = varchar("idempotency_key", 100).uniqueIndex()
    val resultType = varchar("result_type", 50)
    val resultId = varchar("result_id", 100)
    val createdAt = datetime("created_at")

    override val primaryKey = PrimaryKey(id)
}

// ── Refresh Tokens ──
object RefreshTokens : Table("refresh_tokens") {
    val id = uuid("id")
    val userId = uuid("user_id").references(Users.id)
    val tokenHash = varchar("token_hash", 200)
    val deviceId = varchar("device_id", 100).nullable()
    val expiresAt = datetime("expires_at")
    val isRevoked = bool("is_revoked").default(false)
    val createdAt = datetime("created_at")

    override val primaryKey = PrimaryKey(id)
}

// ── Audit Log ──
object AuditLogs : Table("audit_logs") {
    val id = uuid("id")
    val actorId = uuid("actor_id").nullable()
    val action = varchar("action", 100)
    val resourceType = varchar("resource_type", 50).nullable()
    val resourceId = varchar("resource_id", 100).nullable()
    val details = text("details").nullable()
    val ipAddress = varchar("ip_address", 45).nullable()
    val createdAt = datetime("created_at")

    override val primaryKey = PrimaryKey(id)
}

// ── Catalog version tracker ──
object CatalogVersion : Table("catalog_version") {
    val id = integer("id")
    val version = integer("version").default(1)
    val updatedAt = datetime("updated_at")

    override val primaryKey = PrimaryKey(id)
}
