package io.github.hitoshiichikawa.keynest.ui.settings

import io.github.hitoshiichikawa.keynest.domain.model.AppInfo
import io.github.hitoshiichikawa.keynest.domain.model.AutofillStatus
import io.github.hitoshiichikawa.keynest.domain.model.DeviceLockStatus
import io.github.hitoshiichikawa.keynest.domain.model.VaultMetadata

/**
 * Snapshot of everything the Settings screen renders.
 *
 * Issue #10 Req 2.x, 3.x, 4.x, 5.x. The five fields are produced by
 * [SettingsViewModel] from four independent sources (autofill probe,
 * lock probe, metadata Flow, storage suspend, app info) and combined
 * into one immutable state so the Activity binds in a single call.
 */
data class SettingsUiState(
    val autofillStatus: AutofillStatus,
    val lockStatus: DeviceLockStatus,
    val metadata: VaultMetadata,
    val storageBytes: Long,
    val appInfo: AppInfo,
) {
    companion object {
        /**
         * Initial placeholder for `stateIn(initialValue = ...)`. The
         * first real emission overrides every field; this exists so the
         * Activity can render scaffold structure before the first
         * combine cycle finishes.
         */
        val EMPTY: SettingsUiState = SettingsUiState(
            autofillStatus = AutofillStatus.NotEnabled,
            lockStatus = DeviceLockStatus.DeviceCredentialOnly,
            metadata = VaultMetadata(count = 0, latestUpdatedAt = null),
            storageBytes = 0L,
            appInfo = AppInfo(versionName = "", versionCode = 0L),
        )
    }
}
