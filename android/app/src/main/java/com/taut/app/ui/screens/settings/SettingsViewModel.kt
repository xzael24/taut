package com.taut.app.ui.screens.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.taut.app.data.repository.OperatorRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * ViewModel for the Settings screen.
 * Provides operator profile info and app configuration options
 * loaded from the local database.
 */
@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val operatorRepository: OperatorRepository
) : ViewModel() {

    private val _operatorName = MutableStateFlow("Operator")
    val operatorName: StateFlow<String> = _operatorName.asStateFlow()

    private val _bankName = MutableStateFlow("Bank Sampah Melati")
    val bankName: StateFlow<String> = _bankName.asStateFlow()

    val appVersion: String = "0.1.0-alpha"

    /** Dashboard URL (configured during setup). */
    val dashboardUrl: String = "https://taut.id/dashboard"

    init {
        viewModelScope.launch {
            loadOperatorData()
        }
    }

    private suspend fun loadOperatorData() {
        try {
            val profiles = operatorRepository.getActiveProfiles()
            if (profiles.isNotEmpty()) {
                val profile = profiles.first()
                // Try to get the user's real name from the UserEntity
                val user = operatorRepository.getUserById(profile.userId)
                if (user?.name != null && user.name.isNotBlank()) {
                    _operatorName.value = user.name
                } else {
                    // Fall back to the first active operator profile name
                    _operatorName.value = "Operator #${profile.id.take(8)}"
                }
                // Use bankSampahId to identify the bank
                if (profile.bankSampahId.isNotBlank() && profile.bankSampahId != "local") {
                    _bankName.value = "Bank Sampah ${profile.bankSampahId.take(8)}"
                }
            }
        } catch (_: Exception) {
            // Keep defaults on failure
        }
    }

    suspend fun getActiveOperatorCount(): Int =
        operatorRepository.getActiveProfiles().size
}
