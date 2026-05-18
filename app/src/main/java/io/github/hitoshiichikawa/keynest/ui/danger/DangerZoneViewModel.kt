package io.github.hitoshiichikawa.keynest.ui.danger

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import io.github.hitoshiichikawa.keynest.domain.model.ClearVaultFailure
import io.github.hitoshiichikawa.keynest.domain.usecase.ClearVaultUseCase
import io.github.hitoshiichikawa.keynest.util.SafeLogger
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * Backs [DangerZoneActivity]. Issue #10 Req 7.2, 7.3, 7.4, 7.5, 7.6,
 * 7.7, NFR 1.3.
 *
 * Implements the destructive-action state machine. The transitions
 * intentionally treat the destructive sink [DangerZoneUiState.Clearing]
 * as reachable ONLY from [DangerZoneUiState.Confirming], which is
 * itself reachable ONLY from [DangerZoneUiState.Authenticating]
 * success. Every other input (cancel, dismiss) bounces back to
 * [DangerZoneUiState.Idle], so the vault is never cleared without
 * (a) the biometric / device credential AND (b) the confirm dialog.
 *
 * The actual BiometricPrompt is owned by the Activity, since the
 * AndroidX BiometricPrompt requires a `FragmentActivity` and a
 * matching lifecycle.
 */
class DangerZoneViewModel(
    private val clearVault: ClearVaultUseCase,
) : ViewModel() {

    private val _uiState = MutableStateFlow<DangerZoneUiState>(DangerZoneUiState.Idle)
    val uiState: StateFlow<DangerZoneUiState> = _uiState.asStateFlow()

    /**
     * User tapped the "Delete all credentials" button OR the
     * "Retry" button after a previous failure. Both surface as an
     * Idle/Failed -> Authenticating transition.
     */
    fun onClearRequested() {
        val current = _uiState.value
        if (current is DangerZoneUiState.Idle || current is DangerZoneUiState.Failed) {
            _uiState.value = DangerZoneUiState.Authenticating
        }
    }

    /**
     * Activity callback after BiometricPrompt resolves successfully.
     * Transitions Authenticating -> Confirming so the Activity can
     * show the "취소 不可" / "cannot be undone" dialog.
     */
    fun onAuthSucceeded() {
        if (_uiState.value === DangerZoneUiState.Authenticating) {
            _uiState.value = DangerZoneUiState.Confirming
        }
    }

    /**
     * Activity callback after BiometricPrompt cancels or fails.
     * Req 7.3: NO destructive action is taken.
     */
    fun onAuthCancelled() {
        if (_uiState.value === DangerZoneUiState.Authenticating) {
            _uiState.value = DangerZoneUiState.Idle
        }
    }

    /**
     * Activity callback after the confirm dialog's positive button.
     * Transitions Confirming -> Clearing -> Cleared / Failed.
     */
    fun onConfirmed() {
        if (_uiState.value !== DangerZoneUiState.Confirming) return
        _uiState.value = DangerZoneUiState.Clearing
        viewModelScope.launch {
            val result = clearVault()
            _uiState.value = result.fold(
                onSuccess = { DangerZoneUiState.Cleared },
                onFailure = { cause ->
                    val reason = cause as? ClearVaultFailure
                        ?: ClearVaultFailure.Storage(reason = cause.javaClass.simpleName)
                    // NFR 1.2: log only the class name, never the cause message
                    // or the stack trace.
                    SafeLogger.warn(
                        tag = TAG,
                        message = "vault clear failed reason=${reason.javaClass.simpleName}",
                    )
                    DangerZoneUiState.Failed(reason)
                },
            )
        }
    }

    /**
     * Activity callback after the confirm dialog's negative button or
     * outside-tap dismiss. Req 7.3 mirror.
     */
    fun onConfirmCancelled() {
        if (_uiState.value === DangerZoneUiState.Confirming) {
            _uiState.value = DangerZoneUiState.Idle
        }
    }

    /**
     * Activity callback after the user dismisses a Failed Snackbar via
     * the implicit timeout. Resets the state machine to Idle so the
     * retry button shows again.
     */
    fun dismissFailure() {
        if (_uiState.value is DangerZoneUiState.Failed) {
            _uiState.value = DangerZoneUiState.Idle
        }
    }

    class Factory(
        private val clearVault: ClearVaultUseCase,
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            require(modelClass == DangerZoneViewModel::class.java)
            return DangerZoneViewModel(clearVault) as T
        }
    }

    companion object {
        private const val TAG = "KeyNest.Danger"
    }
}
