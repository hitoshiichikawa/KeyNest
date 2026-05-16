package inc.goodanswers.keynest.domain.usecase

import androidx.biometric.BiometricManager
import inc.goodanswers.keynest.domain.model.DeviceLockStatus

/**
 * Snapshots the device's current biometric + lock-screen configuration.
 *
 * Issue #10 Req 3.1, 3.5. Reads
 * [BiometricManager.canAuthenticate] twice (once for `BIOMETRIC_STRONG`,
 * once for `DEVICE_CREDENTIAL`) and folds the two results into one of
 * the four [DeviceLockStatus] variants the Settings screen renders.
 *
 * The probe is synchronous and cheap (BiometricManager caches the
 * answer for ~200ms), so the use-case is not suspending.
 *
 * Mapping rules (design.md "判定ロジック"):
 *
 * - both `BIOMETRIC_SUCCESS`     -> [DeviceLockStatus.BiometricAndDeviceCredential]
 * - biometric != SUCCESS, but
 *   DEVICE_CREDENTIAL is SUCCESS -> [DeviceLockStatus.DeviceCredentialOnly]
 * - both `BIOMETRIC_ERROR_NONE_ENROLLED` -> [DeviceLockStatus.NoLock]
 * - either `BIOMETRIC_ERROR_SECURITY_UPDATE_REQUIRED` -> [DeviceLockStatus.UpdateRequired]
 * - otherwise (no hardware / hw unavailable etc.) fallback to
 *   [DeviceLockStatus.DeviceCredentialOnly]. Most devices that lose
 *   biometric hardware still ship a PIN, and the Settings screen treats
 *   the fallback as "shows a deep-link button to Android Security
 *   settings" which is safe under either state.
 */
class GetDeviceLockStatusUseCase(
    private val biometricManager: BiometricManager,
) {

    operator fun invoke(): DeviceLockStatus {
        val biometric = biometricManager.canAuthenticate(
            BiometricManager.Authenticators.BIOMETRIC_STRONG,
        )
        val deviceCredential = biometricManager.canAuthenticate(
            BiometricManager.Authenticators.DEVICE_CREDENTIAL,
        )

        // Highest-priority signal: a stale security update blocks the
        // biometric path regardless of the other reading, so the UI
        // should surface that explicitly.
        if (biometric == BiometricManager.BIOMETRIC_ERROR_SECURITY_UPDATE_REQUIRED ||
            deviceCredential == BiometricManager.BIOMETRIC_ERROR_SECURITY_UPDATE_REQUIRED
        ) {
            return DeviceLockStatus.UpdateRequired
        }

        return when {
            biometric == BiometricManager.BIOMETRIC_SUCCESS &&
                deviceCredential == BiometricManager.BIOMETRIC_SUCCESS ->
                DeviceLockStatus.BiometricAndDeviceCredential

            deviceCredential == BiometricManager.BIOMETRIC_SUCCESS ->
                DeviceLockStatus.DeviceCredentialOnly

            biometric == BiometricManager.BIOMETRIC_ERROR_NONE_ENROLLED &&
                deviceCredential == BiometricManager.BIOMETRIC_ERROR_NONE_ENROLLED ->
                DeviceLockStatus.NoLock

            else -> DeviceLockStatus.DeviceCredentialOnly
        }
    }
}
