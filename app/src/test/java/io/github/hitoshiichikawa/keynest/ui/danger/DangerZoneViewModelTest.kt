package io.github.hitoshiichikawa.keynest.ui.danger

import io.github.hitoshiichikawa.keynest.domain.model.ClearVaultFailure
import io.github.hitoshiichikawa.keynest.domain.repository.CredentialRepository
import io.github.hitoshiichikawa.keynest.domain.usecase.ClearVaultUseCase
import io.github.hitoshiichikawa.keynest.domain.usecase.FakeCredentialRepository
import io.github.hitoshiichikawa.keynest.security.KeystoreKeyProvider
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Before
import org.junit.Test

/**
 * Behaviour of [DangerZoneViewModel]. Issue #10 Req 7.2, 7.3, 7.4, 7.5,
 * 7.6, 7.7, NFR 1.3.
 *
 * Drives the state machine end-to-end:
 *
 * - Happy path: onClearRequested -> Authenticating -> onAuthSucceeded
 *   -> Confirming -> onConfirmed -> Clearing -> Cleared
 * - Auth cancel: Authenticating -> Idle (no destructive work)
 * - Confirm cancel: Confirming -> Idle (no destructive work)
 * - Storage failure: Confirming -> Clearing -> Failed(Storage)
 * - Keystore failure: Confirming -> Clearing -> Failed(KeystoreAlias)
 * - The state machine refuses out-of-order callbacks (e.g. onConfirmed
 *   while Idle) -- the destructive sink is unreachable without going
 *   through Authenticating + Confirming first (NFR 1.3 invariant).
 */
@OptIn(ExperimentalCoroutinesApi::class)
class DangerZoneViewModelTest {

    private val testDispatcher = StandardTestDispatcher()

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun onClearRequested_fromIdle_transitionsToAuthenticating() {
        val vm = newViewModel()
        assertThat(vm.uiState.value).isEqualTo(DangerZoneUiState.Idle)

        vm.onClearRequested()

        assertThat(vm.uiState.value).isEqualTo(DangerZoneUiState.Authenticating)
    }

    @Test
    fun onAuthSucceeded_movesToConfirming() {
        val vm = newViewModel()
        vm.onClearRequested()

        vm.onAuthSucceeded()

        assertThat(vm.uiState.value).isEqualTo(DangerZoneUiState.Confirming)
    }

    @Test
    fun onAuthCancelled_returnsToIdle_andDoesNotClearVault() = runTest(testDispatcher) {
        // Req 7.3: user cancellation must NOT trigger ClearVaultUseCase.
        val repo = FakeCredentialRepository()
        repo.put(io.github.hitoshiichikawa.keynest.domain.usecase.sampleRecord("a"))
        val vm = newViewModel(repo = repo)
        vm.onClearRequested()

        vm.onAuthCancelled()
        advanceUntilIdle()

        assertThat(vm.uiState.value).isEqualTo(DangerZoneUiState.Idle)
        // Vault is unchanged.
        assertThat(repo.snapshot()).hasSize(1)
    }

    @Test
    fun onConfirmCancelled_returnsToIdle_andDoesNotClearVault() = runTest(testDispatcher) {
        // Req 7.3 mirror: dismissing the dialog must NOT clear the vault.
        val repo = FakeCredentialRepository()
        repo.put(io.github.hitoshiichikawa.keynest.domain.usecase.sampleRecord("a"))
        val vm = newViewModel(repo = repo)
        vm.onClearRequested()
        vm.onAuthSucceeded()

        vm.onConfirmCancelled()
        advanceUntilIdle()

        assertThat(vm.uiState.value).isEqualTo(DangerZoneUiState.Idle)
        assertThat(repo.snapshot()).hasSize(1)
    }

    @Test
    fun onConfirmed_runsClearVault_andReachesCleared() = runTest(testDispatcher) {
        // Req 7.5, 7.6: confirmed -> Clearing -> Cleared, repo empty.
        val repo = FakeCredentialRepository()
        repo.put(io.github.hitoshiichikawa.keynest.domain.usecase.sampleRecord("a"))
        repo.put(io.github.hitoshiichikawa.keynest.domain.usecase.sampleRecord("b"))
        val vm = newViewModel(repo = repo)
        vm.onClearRequested()
        vm.onAuthSucceeded()

        vm.onConfirmed()
        advanceUntilIdle()

        assertThat(vm.uiState.value).isEqualTo(DangerZoneUiState.Cleared)
        assertThat(repo.snapshot()).isEmpty()
    }

    @Test
    fun onConfirmed_surfacesStorageFailure_whenRepoThrows() = runTest(testDispatcher) {
        // Req 7.7: ClearVaultFailure.Storage propagates into Failed.
        val vm = newViewModel(
            repo = ThrowingFakeRepository(throwOnClear = true),
            keystore = StubProvider(),
        )
        vm.onClearRequested()
        vm.onAuthSucceeded()

        vm.onConfirmed()
        advanceUntilIdle()

        val state = vm.uiState.value
        assertThat(state).isInstanceOf(DangerZoneUiState.Failed::class.java)
        assertThat((state as DangerZoneUiState.Failed).reason)
            .isInstanceOf(ClearVaultFailure.Storage::class.java)
    }

    @Test
    fun onConfirmed_surfacesKeystoreFailure_whenDeleteKeyThrows() = runTest(testDispatcher) {
        // Req 7.7: Keystore-side failure propagates as Failed(KeystoreAlias).
        val repo = FakeCredentialRepository()
        repo.put(io.github.hitoshiichikawa.keynest.domain.usecase.sampleRecord("a"))
        val vm = newViewModel(repo = repo, keystore = StubProvider(throwOnDelete = true))
        vm.onClearRequested()
        vm.onAuthSucceeded()

        vm.onConfirmed()
        advanceUntilIdle()

        val state = vm.uiState.value
        assertThat(state).isInstanceOf(DangerZoneUiState.Failed::class.java)
        assertThat((state as DangerZoneUiState.Failed).reason)
            .isInstanceOf(ClearVaultFailure.KeystoreAlias::class.java)
        // DB was cleared first (per ClearVaultUseCase order).
        assertThat(repo.snapshot()).isEmpty()
    }

    @Test
    fun onConfirmed_doesNothing_whenNotInConfirmingState() = runTest(testDispatcher) {
        // NFR 1.3 invariant: the destructive sink is unreachable from
        // Idle / Authenticating without passing through Confirming.
        val repo = FakeCredentialRepository()
        repo.put(io.github.hitoshiichikawa.keynest.domain.usecase.sampleRecord("a"))
        val vm = newViewModel(repo = repo)

        // Attempt to bypass: fire onConfirmed from Idle.
        vm.onConfirmed()
        advanceUntilIdle()

        assertThat(vm.uiState.value).isEqualTo(DangerZoneUiState.Idle)
        assertThat(repo.snapshot()).hasSize(1)
    }

    @Test
    fun onAuthSucceeded_doesNothing_whenNotInAuthenticatingState() {
        // Defensive: callbacks from a stale BiometricPrompt must not
        // skip into Confirming.
        val vm = newViewModel()
        vm.onAuthSucceeded()

        assertThat(vm.uiState.value).isEqualTo(DangerZoneUiState.Idle)
    }

    @Test
    fun onClearRequested_fromFailed_restartsTheFlow() = runTest(testDispatcher) {
        // Req 7.7: the retry button re-runs the use case starting at
        // the BiometricPrompt step (Idle/Failed -> Authenticating).
        val repo = FakeCredentialRepository()
        repo.put(io.github.hitoshiichikawa.keynest.domain.usecase.sampleRecord("a"))
        val provider = StubProvider(throwOnDelete = true)
        val vm = newViewModel(repo = repo, keystore = provider)
        vm.onClearRequested()
        vm.onAuthSucceeded()
        vm.onConfirmed()
        advanceUntilIdle()
        assertThat(vm.uiState.value).isInstanceOf(DangerZoneUiState.Failed::class.java)

        // Act: tap retry. The state machine should re-enter
        // Authenticating.
        vm.onClearRequested()

        assertThat(vm.uiState.value).isEqualTo(DangerZoneUiState.Authenticating)
    }

    @Test
    fun dismissFailure_resetsFailedToIdle() = runTest(testDispatcher) {
        // The Snackbar timeout calls dismissFailure() so the retry
        // button can be shown alongside the destructive button again.
        val vm = newViewModel(
            repo = ThrowingFakeRepository(throwOnClear = true),
        )
        vm.onClearRequested()
        vm.onAuthSucceeded()
        vm.onConfirmed()
        advanceUntilIdle()
        assertThat(vm.uiState.value).isInstanceOf(DangerZoneUiState.Failed::class.java)

        vm.dismissFailure()

        assertThat(vm.uiState.value).isEqualTo(DangerZoneUiState.Idle)
    }

    // ---- helpers --------------------------------------------------------

    private fun newViewModel(
        repo: CredentialRepository = FakeCredentialRepository(),
        keystore: KeystoreKeyProvider = StubProvider(),
    ): DangerZoneViewModel {
        return DangerZoneViewModel(ClearVaultUseCase(repo, keystore))
    }

    /**
     * KeystoreKeyProvider double; can simulate `deleteKey()` failure.
     */
    private class StubProvider(
        private val throwOnDelete: Boolean = false,
    ) : KeystoreKeyProvider() {
        private var alive: Boolean = true
        override fun hasKey(): Boolean = alive
        override fun deleteKey() {
            if (throwOnDelete) throw RuntimeException("simulated KeyStoreException")
            alive = false
        }
    }

    /**
     * Repository proxy that throws on clearAll. Delegates the rest of
     * the surface to a fresh FakeCredentialRepository.
     */
    private class ThrowingFakeRepository(
        private val throwOnClear: Boolean,
    ) : CredentialRepository by FakeCredentialRepository() {
        override suspend fun clearAll() {
            if (throwOnClear) throw RuntimeException("simulated SQLite failure")
        }
    }
}
