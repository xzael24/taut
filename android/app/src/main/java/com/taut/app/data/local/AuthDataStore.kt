package com.taut.app.data.local

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

private const val AUTH_DATASTORE_NAME = "taut_auth"

private val Context.authDataStore: DataStore<Preferences> by preferencesDataStore(
    name = AUTH_DATASTORE_NAME
)

/**
 * Persists authentication state using DataStore Preferences.
 *
 * Stores:
 * - isLoggedIn: Boolean — whether operator has successfully verified PIN
 * - loggedInAt: Long — timestamp (millis) of last successful login
 * - loggedInOperatorId: String — ID of the operator who logged in
 *
 * Session timeout: 24 hours (configurable). After timeout, PIN re-verification is required.
 */
@Singleton
class AuthDataStore @Inject constructor(
    @ApplicationContext private val context: Context
) {

    private val dataStore = context.authDataStore

    // Keys
    companion object {
        private val KEY_IS_LOGGED_IN = booleanPreferencesKey("is_logged_in")
        private val KEY_LOGGED_IN_AT = longPreferencesKey("logged_in_at")
        private val KEY_LOGGED_IN_OPERATOR_ID = stringPreferencesKey("logged_in_operator_id")

        /** Session timeout: 24 hours in milliseconds. */
        const val SESSION_TIMEOUT_MS = 24 * 60 * 60 * 1000L
    }

    /** Whether an operator is currently logged in (and session not expired). */
    val isLoggedIn: Flow<Boolean> = dataStore.data.map { prefs ->
        val loggedIn = prefs[KEY_IS_LOGGED_IN] ?: false
        if (!loggedIn) return@map false

        // Check session expiry
        val loggedInAt = prefs[KEY_LOGGED_IN_AT] ?: 0L
        val now = System.currentTimeMillis()
        if (now - loggedInAt > SESSION_TIMEOUT_MS) return@map false

        true
    }

    /** Timestamp of last successful login. */
    val loggedInAt: Flow<Long> = dataStore.data.map { prefs ->
        prefs[KEY_LOGGED_IN_AT] ?: 0L
    }

    /** ID of the currently logged-in operator. */
    val loggedInOperatorId: Flow<String> = dataStore.data.map { prefs ->
        prefs[KEY_LOGGED_IN_OPERATOR_ID] ?: ""
    }

    /** Get current logged-in state synchronously (for init checks). */
    suspend fun getIsLoggedInSync(): Boolean {
        val prefs = dataStore.data.first()
        val loggedIn = prefs[KEY_IS_LOGGED_IN] ?: false
        if (!loggedIn) return false

        val loggedInAt = prefs[KEY_LOGGED_IN_AT] ?: 0L
        val now = System.currentTimeMillis()
        return (now - loggedInAt <= SESSION_TIMEOUT_MS)
    }

    /** Get current operator ID synchronously. */
    suspend fun getOperatorIdSync(): String {
        return dataStore.data.first()[KEY_LOGGED_IN_OPERATOR_ID] ?: ""
    }

    /** Mark operator as logged in after successful PIN verification. */
    suspend fun setLoggedIn(operatorId: String) {
        dataStore.edit { prefs ->
            prefs[KEY_IS_LOGGED_IN] = true
            prefs[KEY_LOGGED_IN_AT] = System.currentTimeMillis()
            prefs[KEY_LOGGED_IN_OPERATOR_ID] = operatorId
        }
    }

    /** Log out operator (clear auth state). */
    suspend fun logout() {
        dataStore.edit { prefs ->
            prefs[KEY_IS_LOGGED_IN] = false
            prefs[KEY_LOGGED_IN_AT] = 0L
            prefs[KEY_LOGGED_IN_OPERATOR_ID] = ""
        }
    }

    /** Extend session by updating the last login timestamp. */
    suspend fun extendSession() {
        dataStore.edit { prefs ->
            prefs[KEY_LOGGED_IN_AT] = System.currentTimeMillis()
        }
    }
}
