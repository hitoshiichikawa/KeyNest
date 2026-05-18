package io.github.hitoshiichikawa.keynest.ui.settings

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import io.github.hitoshiichikawa.keynest.domain.model.AutofillStatus
import io.github.hitoshiichikawa.keynest.domain.usecase.GetDeviceLockStatusUseCase
import io.github.hitoshiichikawa.keynest.domain.usecase.GetVaultStorageUsageUseCase
import io.github.hitoshiichikawa.keynest.domain.usecase.ObserveVaultMetadataUseCase
import io.github.hitoshiichikawa.keynest.util.AppInfoProvider
import io.github.hitoshiichikawa.keynest.util.AutofillServiceStatus
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

/**
 * Backs [SettingsActivity]. Issue #10 Req 2.x, 3.x, 4.x, 5.x.
 *
 * Composes four sources into one [SettingsUiState] StateFlow:
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
) : ViewModel() {

    private val refreshTick = MutableStateFlow(0)

    val uiState: StateFlow<SettingsUiState> = combine(
        observeMetadata(),
        refreshTick.map { getStorage() },
        refreshTick.map { getLockStatus() },
        refreshTick.map { resolveAutofillStatus() },
    ) { metadata, storage, lock, autofill ->
        SettingsUiState(
            autofillStatus = autofill,
            lockStatus = lock,
            metadata = metadata,
            storageBytes = storage,
            appInfo = appInfoProvider.get(),
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS),
        initialValue = SettingsUiState.EMPTY,
    )

    /**
     * Re-reads autofill / lock / storage (the three non-reactive
     * sources). Called from `Activity.onResume` so users returning
     * from the Android Settings deep-links see the updated state
     * (Req 2.5 / 3.5).
     */
    fun refresh() {
        refreshTick.value = refreshTick.value + 1
    }

    private fun resolveAutofillStatus(): AutofillStatus =
        if (AutofillServiceStatus.isCurrentService(appContext)) AutofillStatus.Enabled
        else AutofillStatus.NotEnabled

    class Factory(
        private val appContext: Context,
        private val observeMetadata: ObserveVaultMetadataUseCase,
        private val getStorage: GetVaultStorageUsageUseCase,
        private val getLockStatus: GetDeviceLockStatusUseCase,
        private val appInfoProvider: AppInfoProvider,
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
            ) as T
        }
    }

    companion object {
        private const val STOP_TIMEOUT_MS = 5_000L
    }
}
