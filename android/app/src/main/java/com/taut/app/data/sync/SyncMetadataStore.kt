package com.taut.app.data.sync

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
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

private val Context.syncDataStore: DataStore<Preferences> by preferencesDataStore(
    name = "taut_sync_metadata"
)

/**
 * Persists sync metadata using DataStore Preferences.
 *
 * Stores:
 * - Lamport clock (monotonic logical clock for conflict resolution)
 * - Last sync cursor (UUIDv7 watermark for incremental sync)
 * - Last successful sync timestamp
 * - Device ID (UUIDv7)
 * - Bank Sampah ID
 */
@Singleton
class SyncMetadataStore @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val dataStore = context.syncDataStore

    // Keys
    companion object {
        private val KEY_LAMPORT_CLOCK = longPreferencesKey("lamport_clock")
        private val KEY_LAST_SYNC_CURSOR = stringPreferencesKey("last_sync_cursor")
        private val KEY_LAST_SYNC_AT = longPreferencesKey("last_sync_at")
        private val KEY_DEVICE_ID = stringPreferencesKey("device_id")
        private val KEY_BANK_SAMPAH_ID = stringPreferencesKey("bank_sampah_id")
    }

    /** Current lamport clock value. */
    val lamportClock: Flow<Long> = dataStore.data.map { prefs ->
        prefs[KEY_LAMPORT_CLOCK] ?: 0L
    }

    /** Last sync cursor for incremental sync. */
    val lastSyncCursor: Flow<String> = dataStore.data.map { prefs ->
        prefs[KEY_LAST_SYNC_CURSOR] ?: ""
    }

    /** Last successful sync timestamp (millis). */
    val lastSyncAt: Flow<Long> = dataStore.data.map { prefs ->
        prefs[KEY_LAST_SYNC_AT] ?: 0L
    }

    /** Device ID (UUIDv7). */
    val deviceId: Flow<String> = dataStore.data.map { prefs ->
        prefs[KEY_DEVICE_ID] ?: ""
    }

    /** Bank Sampah ID. */
    val bankSampahId: Flow<String> = dataStore.data.map { prefs ->
        prefs[KEY_BANK_SAMPAH_ID] ?: ""
    }

    /** Increment and return the new lamport timestamp. */
    suspend fun incrementLamport(): Long {
        dataStore.edit { prefs ->
            val current = prefs[KEY_LAMPORT_CLOCK] ?: 0L
            prefs[KEY_LAMPORT_CLOCK] = current + 1
        }
        return lamportClock.first()
    }

    /** Update the lamport clock to the max of current and incoming value. */
    suspend fun updateLamportFromServer(serverLamport: Long) {
        dataStore.edit { prefs ->
            val current = prefs[KEY_LAMPORT_CLOCK] ?: 0L
            if (serverLamport > current) {
                prefs[KEY_LAMPORT_CLOCK] = serverLamport
            }
        }
    }

    /** Save sync completion state. */
    suspend fun saveSyncCompletion(newCursor: String, serverLamport: Long) {
        dataStore.edit { prefs ->
            prefs[KEY_LAST_SYNC_CURSOR] = newCursor
            prefs[KEY_LAST_SYNC_AT] = System.currentTimeMillis()
            val current = prefs[KEY_LAMPORT_CLOCK] ?: 0L
            if (serverLamport > current) {
                prefs[KEY_LAMPORT_CLOCK] = serverLamport
            }
        }
    }

    /** Set device identity. */
    suspend fun setDeviceIdentity(deviceId: String, bankSampahId: String) {
        dataStore.edit { prefs ->
            prefs[KEY_DEVICE_ID] = deviceId
            prefs[KEY_BANK_SAMPAH_ID] = bankSampahId
        }
    }
}
