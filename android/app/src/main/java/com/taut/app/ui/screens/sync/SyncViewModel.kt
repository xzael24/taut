package com.taut.app.ui.screens.sync

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.taut.app.data.sync.SyncMetadataStore
import com.taut.app.util.SyncScheduler
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * UI state for the sync screen.
 */
data class SyncUiState(
    val isSyncing: Boolean = false,
    val lastSyncAt: String = "Belum pernah sinkron",
    val pendingCount: Int = 0,
    val deviceId: String = "",
    val lamportClock: Long = 0L
)

/**
 * ViewModel for managing sync state and triggering manual sync.
 *
 * Exposes:
 * - Sync UI state (last sync time, pending count, device info)
 * - triggerSyncNow() for "Sync Now" button
 * - schedulePeriodicSync() for initial setup
 */
@HiltViewModel
class SyncViewModel @Inject constructor(
    private val syncScheduler: SyncScheduler,
    private val syncMetadataStore: SyncMetadataStore
) : ViewModel() {

    private val _uiState = MutableStateFlow(SyncUiState())
    val uiState: StateFlow<SyncUiState> = _uiState.asStateFlow()

    init {
        // Observe sync metadata
        viewModelScope.launch {
            syncMetadataStore.lastSyncAt.collect { lastSyncAt ->
                _uiState.value = _uiState.value.copy(
                    lastSyncAt = if (lastSyncAt > 0) {
                        val sdf = java.text.SimpleDateFormat("dd MMM yyyy HH:mm", java.util.Locale("id", "ID"))
                        sdf.format(java.util.Date(lastSyncAt))
                    } else {
                        "Belum pernah sinkron"
                    }
                )
            }
        }

        viewModelScope.launch {
            syncMetadataStore.deviceId.collect { deviceId ->
                _uiState.value = _uiState.value.copy(deviceId = deviceId)
            }
        }

        viewModelScope.launch {
            syncMetadataStore.lamportClock.collect { clock ->
                _uiState.value = _uiState.value.copy(lamportClock = clock)
            }
        }
    }

    /**
     * Trigger an immediate sync.
     * Called when user taps "Sync Now" button.
     */
    fun triggerSyncNow() {
        _uiState.value = _uiState.value.copy(isSyncing = true)
        syncScheduler.triggerSyncNow()
        // Reset syncing flag after a delay (UI feedback)
        viewModelScope.launch {
            kotlinx.coroutines.delay(2000)
            _uiState.value = _uiState.value.copy(isSyncing = false)
        }
    }

    /**
     * Schedule periodic background sync.
     * Called once during app initialization.
     */
    fun schedulePeriodicSync() {
        syncScheduler.schedulePeriodicSync()
    }

    /**
     * Initialize device identity from stored values.
     * Called after device registration completes.
     */
    fun initDeviceIdentity(deviceId: String, bankSampahId: String) {
        viewModelScope.launch {
            syncMetadataStore.setDeviceIdentity(deviceId, bankSampahId)
        }
    }
}
