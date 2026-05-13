package com.example.keynest.domain.usecase

import androidx.biometric.BiometricManager
import com.example.keynest.domain.model.DeviceLockStatus
import com.google.common.truth.Truth.assertThat
import io.mockk.every
import io.mockk.mockk
import org.junit.Test

/**
 * Behaviour of [GetDeviceLockStatusUseCase]. Issue #10 Req 3.1, 3.5.
 *
 * MockK stubs the two `canAuthenticate` reads so we can exhaustively
 * exercise the 4 [DeviceLockStatus] variants plus the fallback.
 */
class GetDeviceLockStatusUseCaseTest {

    private fun useCase(
        biometric: Int,
        deviceCredential: Int,
    ): GetDeviceLockStatusUseCase {
        val manager = mockk<BiometricManager>(relaxed = true)
        every {
            manager.canAuthenticate(BiometricManager.Authenticators.BIOMETRIC_STRONG)
        } returns biometric
        every {
            manager.canAuthenticate(BiometricManager.Authenticators.DEVICE_CREDENTIAL)
        } returns deviceCredential
        return GetDeviceLockStatusUseCase(manager)
    }

    @Test
    fun invoke_returnsBiometricAndDeviceCredential_whenBothSucceed() {
        val result = useCase(
            biometric = BiometricManager.BIOMETRIC_SUCCESS,
            deviceCredential = BiometricManager.BIOMETRIC_SUCCESS,
        ).invoke()

        assertThat(result).isEqualTo(DeviceLockStatus.BiometricAndDeviceCredential)
    }

    @Test
    fun invoke_returnsDeviceCredentialOnly_whenBiometricIsNotEnrolled() {
        val result = useCase(
            biometric = BiometricManager.BIOMETRIC_ERROR_NONE_ENROLLED,
            deviceCredential = BiometricManager.BIOMETRIC_SUCCESS,
        ).invoke()

        assertThat(result).isEqualTo(DeviceLockStatus.DeviceCredentialOnly)
    }

    @Test
    fun invoke_returnsNoLock_whenBothNoneEnrolled() {
        val result = useCase(
            biometric = BiometricManager.BIOMETRIC_ERROR_NONE_ENROLLED,
            deviceCredential = BiometricManager.BIOMETRIC_ERROR_NONE_ENROLLED,
        ).invoke()

        assertThat(result).isEqualTo(DeviceLockStatus.NoLock)
    }

    @Test
    fun invoke_returnsUpdateRequired_whenBiometricSecurityUpdateNeeded() {
        val result = useCase(
            biometric = BiometricManager.BIOMETRIC_ERROR_SECURITY_UPDATE_REQUIRED,
            deviceCredential = BiometricManager.BIOMETRIC_SUCCESS,
        ).invoke()

        assertThat(result).isEqualTo(DeviceLockStatus.UpdateRequired)
    }

    @Test
    fun invoke_returnsUpdateRequired_whenDeviceCredentialSecurityUpdateNeeded() {
        // Belt-and-braces: the use case checks both reads for the
        // SECURITY_UPDATE_REQUIRED signal because either may surface it.
        val result = useCase(
            biometric = BiometricManager.BIOMETRIC_SUCCESS,
            deviceCredential = BiometricManager.BIOMETRIC_ERROR_SECURITY_UPDATE_REQUIRED,
        ).invoke()

        assertThat(result).isEqualTo(DeviceLockStatus.UpdateRequired)
    }

    @Test
    fun invoke_fallsBackToDeviceCredentialOnly_whenHardwareUnavailable() {
        // E.g. a device with broken fingerprint hardware but a working
        // PIN -- the Settings screen still wants to deep-link Security
        // settings, so we surface DeviceCredentialOnly as a safe default.
        val result = useCase(
            biometric = BiometricManager.BIOMETRIC_ERROR_HW_UNAVAILABLE,
            deviceCredential = BiometricManager.BIOMETRIC_ERROR_HW_UNAVAILABLE,
        ).invoke()

        assertThat(result).isEqualTo(DeviceLockStatus.DeviceCredentialOnly)
    }
}
