package com.taut.app.data.repository

import com.taut.app.data.local.dao.OperatorProfileDao
import com.taut.app.data.local.dao.UserDao
import com.taut.app.data.local.entity.OperatorProfileEntity
import com.taut.app.data.local.entity.UserEntity
import com.taut.app.util.CryptoManager
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Repository for operator and user authentication.
 * Handles PIN verification (offline bcrypt) and operator profile management.
 */
@Singleton
class OperatorRepository @Inject constructor(
    private val operatorProfileDao: OperatorProfileDao,
    private val userDao: UserDao
) {
    /** Get all operator profiles. */
    suspend fun getActiveProfiles(): List<OperatorProfileEntity> =
        operatorProfileDao.getActiveProfiles()

    /** Get operator profile by user ID. */
    suspend fun getProfileByUserId(userId: String): OperatorProfileEntity? =
        operatorProfileDao.getProfileByUserId(userId)

    /** Get operator profile by ID. */
    suspend fun getProfileById(id: String): OperatorProfileEntity? =
        operatorProfileDao.getProfileById(id)

    /** Insert a new operator profile. */
    suspend fun saveProfile(profile: OperatorProfileEntity) =
        operatorProfileDao.insert(profile)

    /** Update an operator profile. */
    suspend fun updateProfile(profile: OperatorProfileEntity) =
        operatorProfileDao.update(profile)

    /** Get all users. */
    fun getAllUsers(): Flow<List<UserEntity>> = userDao.getAllUsers()

    /** Get user by phone number. */
    suspend fun getUserByPhone(phone: String): UserEntity? = userDao.getUserByPhone(phone)

    /** Get user by ID. */
    suspend fun getUserById(id: String): UserEntity? = userDao.getUserById(id)

    /** Insert or update a user. */
    suspend fun saveUser(user: UserEntity) = userDao.insert(user)

    /** Bulk insert users. */
    suspend fun saveAllUsers(users: List<UserEntity>) = userDao.insertAll(users)

    /**
     * Verify PIN against stored bcrypt hash.
     *
     * Uses CryptoManager.bcryptHash / verifyBcrypt with cost factor 12.
     * Only bcrypt hashes are accepted — SHA-256 legacy hashes are not supported.
     */
    suspend fun verifyPin(pin: String): Boolean {
        val profiles = operatorProfileDao.getActiveProfiles()
        return profiles.any { profile ->
            CryptoManager.verifyBcrypt(pin, profile.pinHash)
        }
    }

    /**
     * Verify PIN for a specific user by user ID.
     * Much more efficient than verifyPin() because it does a direct lookup
     * instead of iterating all active profiles.
     */
    suspend fun verifyPinForUser(pin: String, userId: String): Boolean {
        val profile = operatorProfileDao.getProfileByUserId(userId) ?: return false
        return CryptoManager.verifyBcrypt(pin, profile.pinHash)
    }

    /**
     * Verify PIN against a specific profile identified by profile ID.
     * Most efficient option — skips all lookups entirely.
     */
    suspend fun verifyPinForProfile(pin: String, profileId: String): Boolean {
        val profile = operatorProfileDao.getProfileById(profileId) ?: return false
        return CryptoManager.verifyBcrypt(pin, profile.pinHash)
    }
}
