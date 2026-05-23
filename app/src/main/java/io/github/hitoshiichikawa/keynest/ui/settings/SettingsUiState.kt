package io.github.hitoshiichikawa.keynest.ui.settings

import io.github.hitoshiichikawa.keynest.domain.model.AppInfo
import io.github.hitoshiichikawa.keynest.domain.model.AutofillStatus
import io.github.hitoshiichikawa.keynest.domain.model.DeviceLockStatus
import io.github.hitoshiichikawa.keynest.domain.model.PasskeyProviderStatus
import io.github.hitoshiichikawa.keynest.domain.model.VaultMetadata

/**
 * Snapshot of everything the Settings screen renders.
 *
 * Issue #10 Req 2.x, 3.x, 4.x, 5.x — five original fields produced by
 * [SettingsViewModel] from four independent sources (autofill probe,
 * lock probe, metadata Flow, storage suspend, app info).
 *
 * Issue #103 (Phase 6, umbrella #89 分割案 7) Req 3.2 / 5.2 adds a sixth
 * field [passkeyProviderStatus] in an additive fashion; the existing five
 * field names and types are preserved (Req 5.2 — no signature break).
 */
data class SettingsUiState(
    val autofillStatus: AutofillStatus,
    val lockStatus: DeviceLockStatus,
    val metadata: VaultMetadata,
    val storageBytes: Long,
    val appInfo: AppInfo,
    val passkeyProviderStatus: PasskeyProviderStatus,
) {
    companion object {
        /**
         * Initial placeholder for `stateIn(initialValue = ...)`. The
         * first real emission overrides every field; this exists so the
         * Activity can render scaffold structure before the first
         * combine cycle finishes.
         *
         * The PassKey provider status defaults to
         * [PasskeyProviderStatus.Unsupported] — the safest neutral state
         * (Req 3.5 "never falsely report Enabled"). On API 34+ devices
         * this is overwritten by the first emission of the StateFlow.
         */
        val EMPTY: SettingsUiState = SettingsUiState(
            autofillStatus = AutofillStatus.NotEnabled,
            lockStatus = DeviceLockStatus.DeviceCredentialOnly,
            metadata = VaultMetadata(count = 0, latestUpdatedAt = null),
            storageBytes = 0L,
            appInfo = AppInfo(versionName = "", versionCode = 0L),
            passkeyProviderStatus = PasskeyProviderStatus.Unsupported,
        )
    }
}
