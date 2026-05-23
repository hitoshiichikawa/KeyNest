package io.github.hitoshiichikawa.keynest.ui.settings

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import io.github.hitoshiichikawa.keynest.domain.model.AutofillStatus
import io.github.hitoshiichikawa.keynest.domain.model.PasskeyProviderStatus
import io.github.hitoshiichikawa.keynest.domain.usecase.GetDeviceLockStatusUseCase
import io.github.hitoshiichikawa.keynest.domain.usecase.GetVaultStorageUsageUseCase
import io.github.hitoshiichikawa.keynest.domain.usecase.ObserveVaultMetadataUseCase
import io.github.hitoshiichikawa.keynest.ui.settings.passkey.CredentialProviderStatusChecker
import io.github.hitoshiichikawa.keynest.util.AppInfoProvider
import io.github.hitoshiichikawa.keynest.util.AutofillServiceStatus
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.withContext

/**
 * Backs [SettingsActivity]. Issue #10 Req 2.x, 3.x, 4.x, 5.x +
 * Issue #103 Req 3.1 / 3.6 / 3.7 / 5.2 / 5.3.
 *
 * Composes five sources into one [SettingsUiState] StateFlow:
 *
 *   1. `observeMetadata()` -- reactive Flow<VaultMetadata>. Re-emits
 *      whenever any credential is saved / updated / deleted (so the
 *      Settings screen reflects changes made by other flows while it
 *      is open, e.g. after a Vault clear).
 *   2. Storage bytes -- suspended `File.length()` on Dispatchers.IO;
 *      re-read on every [refresh] tick.
 *   3. Device lock status -- synchronous `BiometricManager.canAuthenticate`;
 *      re-read on every [refresh] tick.
 *   4. Autofill status -- synchronous AutofillServiceStatus probe;
 *      re-read on every [refresh] tick (Req 2.5: returning from
 *      Android Settings refreshes the badge).
 *   5. PassKey provider status (Issue #103) -- 3-valued enum produced by
 *      [CredentialProviderStatusChecker]. Probed off the main thread via
 *      `withContext(IO)` (Req 3.6 / NFR 1.1, 1.2) and re-read on every
 *      [refresh] tick so returning from the OS Credential Manager
 *      settings screen reflects the new state (Req 3.7 / 5.3).
 *
 * [refresh] increments an internal tick StateFlow that every `map`
 * branch is subscribed to. This lets the Activity call refresh()
 * from `onResume` without re-creating the ViewModel.
 */
class SettingsViewModel(
    private val appContext: Context,
    private val observeMetadata: ObserveVaultMetadataUseCase,
    private val getStorage: GetVaultStorageUsageUseCase,
    private val getLockStatus: GetDeviceLockStatusUseCase,
    private val appInfoProvider: AppInfoProvider,
    private val credentialProviderStatusChecker: CredentialProviderStatusChecker,
) : ViewModel() {

    private val refreshTick = MutableStateFlow(0)

    val uiState: StateFlow<SettingsUiState> = combine(
        observeMetadata(),
        refreshTick.map { getStorage() },
        refreshTick.map { getLockStatus() },
        refreshTick.map { resolveAutofillStatus() },
        refreshTick.map { resolvePasskeyProviderStatus() },
    ) { metadata, storage, lock, autofill, passkey ->
        SettingsUiState(
            autofillStatus = autofill,
            lockStatus = lock,
            metadata = metadata,
            storageBytes = storage,
            appInfo = appInfoProvider.get(),
            passkeyProviderStatus = passkey,
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS),
        initialValue = SettingsUiState.EMPTY,
    )

    /**
     * Re-reads autofill / lock / storage / PassKey provider (the four
     * non-reactive sources). Called from `Activity.onResume` so users
     * returning from the Android Settings / Credential Manager deep-links
     * see the updated state (Issue #10 Req 2.5 / 3.5 + Issue #103 Req
     * 3.7 / 5.3).
     */
    fun refresh() {
        refreshTick.value = refreshTick.value + 1
    }

    private fun resolveAutofillStatus(): AutofillStatus =
        if (AutofillServiceStatus.isCurrentService(appContext)) AutofillStatus.Enabled
        else AutofillStatus.NotEnabled

    /**
     * Issue #103 Req 3.1 / 3.6: the checker call is wrapped in
     * `withContext(IO)` because `Settings.Secure.getString` can involve
     * IPC and we must not block the main thread (NFR 1.1 / 1.2). The
     * checker itself never throws — exceptions are caught internally and
     * downgrade to [PasskeyProviderStatus.Disabled] (Req 3.5).
     */
    private suspend fun resolvePasskeyProviderStatus(): PasskeyProviderStatus =
        withContext(Dispatchers.IO) { credentialProviderStatusChecker.check() }

    class Factory(
        private val appContext: Context,
        private val observeMetadata: ObserveVaultMetadataUseCase,
        private val getStorage: GetVaultStorageUsageUseCase,
        private val getLockStatus: GetDeviceLockStatusUseCase,
        private val appInfoProvider: AppInfoProvider,
        private val credentialProviderStatusChecker: CredentialProviderStatusChecker,
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            require(modelClass == SettingsViewModel::class.java)
            return SettingsViewModel(
                appContext = appContext,
                observeMetadata = observeMetadata,
                getStorage = getStorage,
                getLockStatus = getLockStatus,
                appInfoProvider = appInfoProvider,
                credentialProviderStatusChecker = credentialProviderStatusChecker,
            ) as T
        }
    }

    companion object {
        private const val STOP_TIMEOUT_MS = 5_000L
    }
}
