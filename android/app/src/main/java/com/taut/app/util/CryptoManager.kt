package com.taut.app.util

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import at.favre.lib.crypto.bcrypt.BCrypt
import java.security.KeyStore
import javax.crypto.KeyGenerator

/**
 * Android Keystore wrapper for secure key management.
 *
 * Handles:
 * - Database encryption key generation/storage
 * - HMAC key for transaction signing
 * - PIN bcrypt hashing
 * - Transaction signing with device key
 *
 * Per architecture.md §6.1:
 * - AES-256-GCM encryption at rest
 * - Keys stored in Android Keystore (hardware-backed when available)
 * - PINs hashed with bcrypt (cost factor 12)
 */
object CryptoManager {

    private const val KEYSTORE_PROVIDER = "AndroidKeyStore"
    private const val DB_KEY_ALIAS = "taut_db_encryption_key"
    private const val HMAC_KEY_ALIAS = "taut_hmac_signing_key"

    // bcrypt cost factor — OWASP recommends 10-12 for 2024
    private const val BCRYPT_COST = 12

    /**
     * Get or create the database encryption key from Android Keystore.
     * Used for SQLCipher database encryption.
     */
    fun getOrCreateDbKey(): ByteArray {
        val keyStore = KeyStore.getInstance(KEYSTORE_PROVIDER).apply { load(null) }

        if (keyStore.containsAlias(DB_KEY_ALIAS)) {
            val entry = keyStore.getEntry(DB_KEY_ALIAS, null) as KeyStore.SecretKeyEntry
            return entry.secretKey.encoded
        }

        // Generate new key
        val keyGenerator = KeyGenerator.getInstance(
            KeyProperties.KEY_ALGORITHM_AES,
            KEYSTORE_PROVIDER
        )
        val spec = KeyGenParameterSpec.Builder(
            DB_KEY_ALIAS,
            KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
        )
            .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
            .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
            .setKeySize(256)
            .build()

        keyGenerator.init(spec)
        val secretKey = keyGenerator.generateKey()
        return secretKey.encoded
    }

    /**
     * Hash a PIN using bcrypt with a random salt.
     *
     * @param pin The plaintext PIN to hash
     * @return bcrypt hash string (includes embedded salt and cost)
     */
    fun bcryptHash(pin: String): String {
        return BCrypt.withDefaults().hashToString(BCRYPT_COST, pin.toCharArray())
    }

    /**
     * Verify a PIN against a stored bcrypt hash.
     *
     * @param pin The plaintext PIN to verify
     * @param hash The stored bcrypt hash
     * @return true if the PIN matches the hash
     */
    fun verifyBcrypt(pin: String, hash: String): Boolean {
        return BCrypt.verifyer().verify(pin.toCharArray(), hash).verified
    }

    // TODO: Implement HMAC key management
    // TODO: Implement transaction signing with device key
}
