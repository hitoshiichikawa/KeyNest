package com.example.keynest.domain.model

/**
 * Read-only summary of the device's lock / biometric configuration.
 *
 * Issue #10 Req 3.1, 3.5. The Settings screen renders one localized
 * string per variant. The KeyNest app intentionally does NOT offer an
 * in-app toggle for any of these states (Req 3.2); changing the device
 * lock method is delegated to Android's Security settings.
 *
 * Mapping from BiometricManager.canAuthenticate is performed by
 * [com.example.keynest.domain.usecase.GetDeviceLockStatusUseCase] -- see
 * its KDoc for the precise rules.
 */
sealed interface DeviceLockStatus {

    /** Biometric (BIOMETRIC_STRONG) AND a device credential (PIN / pattern / password). */
    data object BiometricAndDeviceCredential : DeviceLockStatus

    /** Device credential only -- no enrolled biometric, but a lock screen exists. */
    data object DeviceCredentialOnly : DeviceLockStatus

    /** No lock screen and no biometric -- the device is fully open. */
    data object NoLock : DeviceLockStatus

    /** A security update is required for biometric hardware to be usable. */
    data object UpdateRequired : DeviceLockStatus
}
