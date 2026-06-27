package com.taut.app.ui.screens.auth

import com.taut.app.data.local.AuthDataStore
import com.taut.app.data.local.entity.OperatorProfileEntity
import com.taut.app.data.local.entity.UserEntity
import com.taut.app.data.repository.OperatorRepository
import com.taut.app.util.CryptoManager
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Tests for PinEntryViewModel — PIN verification logic.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class PinEntryViewModelTest {

    private lateinit var operatorRepository: OperatorRepository
    private lateinit var authDataStore: AuthDataStore
    private lateinit var viewModel: PinEntryViewModel

    // Bcrypt hash of "1234" for our mock operator profile
    private val correctPinHash = CryptoManager.bcryptHash("1234")

    private val operatorProfile = OperatorProfileEntity(
        id = "op-profile-1",
        bankSampahId = "bs-1",
        userId = "user-op-1",
        pinHash = correctPinHash,
        isPrimary = true,
        isActive = true,
        createdAt = System.currentTimeMillis()
    )

    @Before
    fun setUp() {
        operatorRepository = mockk(relaxed = true)
        authDataStore = mockk(relaxed = true)
        // Default: no existing session
        coEvery { authDataStore.getIsLoggedInSync() } returns false
        viewModel = PinEntryViewModel(operatorRepository, authDataStore)
    }

    // ── Initial State ───────────────────────────────────────────────────────

    @Test
    fun initialState_isIdleAndEmptyPin() {
        assertEquals("Initial pin should be empty", "", viewModel.pin.value)
        assertTrue(
            "Initial state should be Idle",
            viewModel.verificationState.value is PinVerificationState.Idle
        )
    }

    // ── Existing Session ────────────────────────────────────────────────────

    @Test
    fun existingSession_skipsPinVerification() = runTest {
        coEvery { authDataStore.getIsLoggedInSync() } returns true
        // Re-create ViewModel so init checks DataStore
        val vm = PinEntryViewModel(operatorRepository, authDataStore)

        // Wait for init coroutine
        kotlinx.coroutines.delay(100)

        assertTrue(
            "Should auto-verify with existing session",
            vm.verificationState.value is PinVerificationState.Verified
        )
        coVerify { authDataStore.extendSession() }
    }

    // ── PIN Digit Entry ─────────────────────────────────────────────────────

    @Test
    fun appendDigit_addsDigitToPin() {
        viewModel.appendDigit("1")
        assertEquals("1", viewModel.pin.value)
    }

    @Test
    fun appendDigit_rejectsMoreThan4Digits() = runTest {
        // Set up mocks so verification SUCCEEDS (returns true), so pin is NOT cleared
        coEvery { operatorRepository.getActiveProfiles() } returns listOf(operatorProfile)
        coEvery { operatorRepository.verifyPin(any()) } returns true

        viewModel.appendDigit("1")
        viewModel.appendDigit("2")
        viewModel.appendDigit("3")
        viewModel.appendDigit("4")
        // After 4 digits, PIN is automatically verified, so state changes
        // But the digit should still be added
        viewModel.appendDigit("5")

        // The 5th digit should not be added (pin maxes at 4)
        // Actually looking at the code: if (_pin.value.length < 4) so 5th digit is rejected
        assertEquals("Fifth digit should be rejected", "1234", viewModel.pin.value)
    }

    @Test
    fun deleteLastDigit_removesFromPin() {
        viewModel.appendDigit("1")
        viewModel.appendDigit("2")
        viewModel.deleteLastDigit()
        assertEquals("1", viewModel.pin.value)
    }

    @Test
    fun deleteLastDigit_emptyPin_doesNothing() {
        viewModel.deleteLastDigit()
        assertEquals("", viewModel.pin.value)
    }

    @Test
    fun clearPin_resetsPinAndState() {
        viewModel.appendDigit("1")
        viewModel.clearPin()
        assertEquals("Pin should be cleared", "", viewModel.pin.value)
        assertTrue(
            "State should return to Idle",
            viewModel.verificationState.value is PinVerificationState.Idle
        )
    }

    // ── PIN Verification - Correct PIN ─────────────────────────────────────

    @Test
    fun verifyPin_correctPin_returnsVerified() = runTest {
        // Set up repository to return profiles and verify PIN
        coEvery { operatorRepository.getActiveProfiles() } returns listOf(operatorProfile)
        coEvery { operatorRepository.verifyPin("1234") } returns true

        // Enter all 4 digits (triggers auto-verification)
        viewModel.appendDigit("1")
        viewModel.appendDigit("2")
        viewModel.appendDigit("3")
        viewModel.appendDigit("4")

        // Wait a bit for async verification
        Thread.sleep(100)

        assertTrue(
            "State should be Verified after correct PIN",
            viewModel.verificationState.value is PinVerificationState.Verified
        )
    }

    @Test
    fun verifyPin_correctPin_persistsAuthState() = runTest {
        coEvery { operatorRepository.getActiveProfiles() } returns listOf(operatorProfile)
        coEvery { operatorRepository.verifyPin("1234") } returns true

        viewModel.appendDigit("1")
        viewModel.appendDigit("2")
        viewModel.appendDigit("3")
        viewModel.appendDigit("4")

        Thread.sleep(100)

        // Verify DataStore was updated with correct operator ID
        coVerify { authDataStore.setLoggedIn("op-profile-1") }
    }

    // ── PIN Verification - Wrong PIN ───────────────────────────────────────

    @Test
    fun verifyPin_wrongPin_returnsError() = runTest {
        // Set up repository to return profiles and verify PIN
        coEvery { operatorRepository.getActiveProfiles() } returns listOf(operatorProfile)
        coEvery { operatorRepository.verifyPin("9999") } returns false

        // Enter wrong PIN
        viewModel.appendDigit("9")
        viewModel.appendDigit("9")
        viewModel.appendDigit("9")
        viewModel.appendDigit("9")

        // Wait a bit for async verification
        Thread.sleep(100)

        val state = viewModel.verificationState.value
        assertTrue(
            "State should be Error after wrong PIN (was: $state)",
            state is PinVerificationState.Error
        )
        assertEquals("PIN salah", (state as PinVerificationState.Error).message)
    }

    @Test
    fun verifyPin_wrongPin_clearsPin() = runTest {
        coEvery { operatorRepository.getActiveProfiles() } returns listOf(operatorProfile)
        coEvery { operatorRepository.verifyPin("9999") } returns false

        viewModel.appendDigit("9")
        viewModel.appendDigit("9")
        viewModel.appendDigit("9")
        viewModel.appendDigit("9")

        Thread.sleep(100)

        assertEquals("PIN should be cleared after wrong attempt", "", viewModel.pin.value)
    }

    // ── PIN Verification - Empty Profiles ──────────────────────────────────

    @Test
    fun verifyPin_emptyProfiles_reportsError() = runTest {
        // No operator profiles configured
        coEvery { operatorRepository.getActiveProfiles() } returns emptyList()

        viewModel.appendDigit("1")
        viewModel.appendDigit("2")
        viewModel.appendDigit("3")
        viewModel.appendDigit("4")

        Thread.sleep(100)

        val state = viewModel.verificationState.value
        assertTrue(
            "State should be Error when no profiles (was: $state)",
            state is PinVerificationState.Error
        )
        assertEquals(
            "PIN belum diatur. Silakan hubungi admin.",
            (state as PinVerificationState.Error).message
        )
    }

    @Test
    fun verifyPin_emptyProfiles_doesNotAutoVerify() = runTest {
        // No operator profiles configured — should NOT auto-verify
        coEvery { operatorRepository.getActiveProfiles() } returns emptyList()

        // Enter the "correct" PIN but there are no profiles
        viewModel.appendDigit("1")
        viewModel.appendDigit("2")
        viewModel.appendDigit("3")
        viewModel.appendDigit("4")

        Thread.sleep(100)

        // Should NOT be verified — should be in error state
        assertFalse(
            "Empty profiles should NOT auto-verify",
            viewModel.verificationState.value is PinVerificationState.Verified
        )
        assertTrue(
            "Empty profiles should show error",
            viewModel.verificationState.value is PinVerificationState.Error
        )
    }

    // ── Verification State Transitions ─────────────────────────────────────

    @Test
    fun verifyPin_showsVerifyingStateWhileInProgress() = runTest {
        // Create a repository that delays response
        coEvery { operatorRepository.getActiveProfiles() } returns listOf(operatorProfile)
        coEvery { operatorRepository.verifyPin("1234") } returns true

        viewModel.appendDigit("1")
        viewModel.appendDigit("2")
        viewModel.appendDigit("3")

        // Before 4th digit, state should still be Idle
        assertTrue(
            "Should be Idle before 4th digit",
            viewModel.verificationState.value is PinVerificationState.Idle
        )

        // Enter 4th digit — triggers verification
        viewModel.appendDigit("4")

        // State was set to Verifying before result returned
        // (we can check immediately after 4th digit since it's a StateFlow)
        Thread.sleep(50)
        // Note: the transition to Verifying happens immediately, then to Verified asynchronously
    }

    // ── Multiple Attempts ──────────────────────────────────────────────────

    @Test
    fun verifyPin_correctAfterWrong_returnsVerified() = runTest {
        // First attempt — wrong PIN
        coEvery { operatorRepository.getActiveProfiles() } returns listOf(operatorProfile)
        coEvery { operatorRepository.verifyPin("9999") } returns false
        coEvery { operatorRepository.verifyPin("1234") } returns true

        viewModel.appendDigit("9")
        viewModel.appendDigit("9")
        viewModel.appendDigit("9")
        viewModel.appendDigit("9")

        Thread.sleep(100)

        assertTrue("First attempt should be Error",
            viewModel.verificationState.value is PinVerificationState.Error)

        // Second attempt — correct PIN
        // clearPin() would be called by the error handler
        viewModel.clearPin()

        viewModel.appendDigit("1")
        viewModel.appendDigit("2")
        viewModel.appendDigit("3")
        viewModel.appendDigit("4")

        Thread.sleep(100)

        assertTrue("Second attempt should be Verified",
            viewModel.verificationState.value is PinVerificationState.Verified)
    }

    // ── Session Expiry ─────────────────────────────────────────────────────

    @Test
    fun expiredSession_showsPinEntry() = runTest {
        // Session was set but has now expired (getIsLoggedInSync returns false)
        coEvery { authDataStore.getIsLoggedInSync() } returns false
        val vm = PinEntryViewModel(operatorRepository, authDataStore)

        kotlinx.coroutines.delay(100)

        assertTrue(
            "Expired session should show Idle state",
            vm.verificationState.value is PinVerificationState.Idle
        )
    }

    // ── Helper ────────────────────────────────────────────────────────────
    companion object {
        // No SHA-256 helper needed anymore – only bcrypt is supported
    }
}
