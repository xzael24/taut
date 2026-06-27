package com.taut.app.ui.screens.auth

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.taut.app.data.local.AuthDataStore
import com.taut.app.data.repository.OperatorRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * ViewModel for PIN entry screen.
 * Verifies operator PIN against stored bcrypt hash (offline).
 *
 * Auth state is persisted via [AuthDataStore] so that login survives app restart
 * (session timeout: 24 hours). On init, checks whether a valid session already exists
 * and emits [PinVerificationState.Verified] immediately if so.
 */
@HiltViewModel
class PinEntryViewModel @Inject constructor(
    private val operatorRepository: OperatorRepository,
    private val authDataStore: AuthDataStore
) : ViewModel() {

    private val _pin = MutableStateFlow("")
    val pin: StateFlow<String> = _pin.asStateFlow()

    private val _verificationState = MutableStateFlow<PinVerificationState>(PinVerificationState.Idle)
    val verificationState: StateFlow<PinVerificationState> = _verificationState.asStateFlow()

    init {
        // Check for existing valid session on app start
        viewModelScope.launch {
            val alreadyLoggedIn = authDataStore.getIsLoggedInSync()
            if (alreadyLoggedIn) {
                _verificationState.value = PinVerificationState.Verified
                // Extend session so it doesn't expire mid-use
                authDataStore.extendSession()
            }
        }
    }

    fun appendDigit(digit: String) {
        if (_pin.value.length < 4) {
            _pin.value += digit
            if (_pin.value.length == 4) {
                verifyPin()
            }
        }
    }

    fun deleteLastDigit() {
        if (_pin.value.isNotEmpty()) {
            _pin.value = _pin.value.dropLast(1)
        }
    }

    fun clearPin() {
        _pin.value = ""
        _verificationState.value = PinVerificationState.Idle
    }

    private fun verifyPin() {
        val currentPin = _pin.value
        viewModelScope.launch {
            _verificationState.value = PinVerificationState.Verifying

            val profiles = operatorRepository.getActiveProfiles()
            if (profiles.isEmpty()) {
                // No operators configured — guide user to PIN creation
                clearPin()
                _verificationState.value = PinVerificationState.Error("PIN belum diatur. Silakan hubungi admin.")
                return@launch
            }

            val verified = operatorRepository.verifyPin(currentPin)

            if (verified) {
                // Persist auth state to DataStore
                authDataStore.setLoggedIn(profiles.first().id)
                _verificationState.value = PinVerificationState.Verified
            } else {
                clearPin()
                _verificationState.value = PinVerificationState.Error("PIN salah")
            }
        }
    }
}

sealed class PinVerificationState {
    data object Idle : PinVerificationState()
    data object Verifying : PinVerificationState()
    data object Verified : PinVerificationState()
    data class Error(val message: String) : PinVerificationState()
}
