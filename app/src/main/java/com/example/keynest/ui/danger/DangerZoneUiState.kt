package com.example.keynest.ui.danger

import com.example.keynest.domain.model.ClearVaultFailure

/**
 * State machine for the Danger Zone (Vault clear) screen.
 *
 * Issue #10 Req 7.2, 7.3, 7.4, 7.5, 7.6, 7.7, NFR 1.3. The transitions
 * are enforced by [DangerZoneViewModel] -- the [Clearing] phase is
 * unreachable without going through [Confirming], which is in turn
 * unreachable without [Authenticating] success. The three gates
 * (Activity entry, biometric, confirmation) protect against an
 * accidental destructive action.
 */
sealed interface DangerZoneUiState {
    /** Initial state and the state we land in after a Cancel. */
    data object Idle : DangerZoneUiState

    /** BiometricPrompt is showing. Activity owns the prompt lifecycle. */
    data object Authenticating : DangerZoneUiState

    /**
     * Authentication succeeded; the "this cannot be undone" dialog is
     * showing. Tapping "Delete permanently" transitions to [Clearing].
     */
    data object Confirming : DangerZoneUiState

    /** ClearVaultUseCase is running. UI shows a progress bar. */
    data object Clearing : DangerZoneUiState

    /**
     * The clear completed successfully. Activity should show the
     * "Vault cleared" Snackbar and finish().
     */
    data object Cleared : DangerZoneUiState

    /**
     * The clear failed. [reason] is the structured failure -- the
     * UI surfaces a Snackbar + retry button.
     */
    data class Failed(val reason: ClearVaultFailure) : DangerZoneUiState
}
