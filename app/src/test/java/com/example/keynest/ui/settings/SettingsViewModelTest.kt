package com.example.keynest.ui.settings

import androidx.biometric.BiometricManager
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.keynest.domain.model.AppInfo
import com.example.keynest.domain.model.AutofillStatus
import com.example.keynest.domain.model.CredentialId
import com.example.keynest.domain.model.DeviceLockStatus
import com.example.keynest.domain.model.EncryptedCredentialRecord
import com.example.keynest.domain.model.SigningHash
import com.example.keynest.domain.usecase.FakeCredentialRepository
import com.example.keynest.domain.usecase.GetDeviceLockStatusUseCase
import com.example.keynest.domain.usecase.GetVaultStorageUsageUseCase
import com.example.keynest.domain.usecase.ObserveVaultMetadataUseCase
import com.example.keynest.util.AppInfoProvider
import com.example.keynest.util.VaultStorageMeasurer
import com.google.common.truth.Truth.assertThat
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/**
 * Behaviour of [SettingsViewModel]. Issue #10 Req 2.1, 2.5, 3.1, 3.5,
 * 4.1, 4.2, 4.3, 4.4, 5.1.
 *
 * Drives a real ViewModel against a [FakeCredentialRepository] plus
 * test doubles for the synchronous probes (lock / storage / appInfo /
 * autofill). Runs on Robolectric because the AutofillServiceStatus
 * probe needs a real Application context.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(AndroidJUnit4::class)
@Config(sdk = [33])
class SettingsViewModelTest {

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
    fun uiState_emitsCountAndPlaceholder_onEmptyVault() = runTest(testDispatcher) {
        // Req 4.1, 4.3: empty vault -> count = 0, latestUpdatedAt = null.
        val (vm, _, job) = newViewModelWithCollector(lockStatus = DeviceLockStatus.NoLock)
        try {
            advanceUntilIdle()
            val state = vm.uiState.value
            assertThat(state.metadata.count).isEqualTo(0)
            assertThat(state.metadata.latestUpdatedAt).isNull()
        } finally {
            job.cancel()
        }
    }

    @Test
    fun uiState_reflectsVaultMetadata_acrossRows() = runTest(testDispatcher) {
        // Req 4.1, 4.2: count + max(updated_at) propagate into uiState.
        val (vm, repo, job) = newViewModelWithCollector()
        try {
            repo.put(sample("a", updatedAt = 100L))
            repo.put(sample("b", updatedAt = 500L))
            repo.put(sample("c", updatedAt = 200L))
            advanceUntilIdle()

            val state = vm.uiState.value
            assertThat(state.metadata.count).isEqualTo(3)
            assertThat(state.metadata.latestUpdatedAt).isEqualTo(500L)
        } finally {
            job.cancel()
        }
    }

    @Test
    fun uiState_carriesLockStatus_andAppInfo_fromInjectedSources() = runTest(testDispatcher) {
        // Req 3.1, 5.1.
        val (vm, _, job) = newViewModelWithCollector(
            lockStatus = DeviceLockStatus.BiometricAndDeviceCredential,
            storageBytes = 12_345L,
            appInfo = AppInfo(versionName = "9.9.9", versionCode = 99L),
        )
        try {
            advanceUntilIdle()
            val state = vm.uiState.value
            assertThat(state.lockStatus).isEqualTo(DeviceLockStatus.BiometricAndDeviceCredential)
            assertThat(state.storageBytes).isEqualTo(12_345L)
            assertThat(state.appInfo.versionName).isEqualTo("9.9.9")
            assertThat(state.appInfo.versionCode).isEqualTo(99L)
        } finally {
            job.cancel()
        }
    }

    @Test
    fun uiState_reEmitsMetadata_whenRepositoryMutates() = runTest(testDispatcher) {
        // Req 4.1: reactive Flow emits a fresh metadata after put().
        val (vm, repo, job) = newViewModelWithCollector()
        try {
            advanceUntilIdle()
            assertThat(vm.uiState.value.metadata.count).isEqualTo(0)

            repo.put(sample("a", updatedAt = 999L))
            advanceUntilIdle()

            assertThat(vm.uiState.value.metadata.count).isEqualTo(1)
            assertThat(vm.uiState.value.metadata.latestUpdatedAt).isEqualTo(999L)
        } finally {
            job.cancel()
        }
    }

    @Test
    fun uiState_reEmitsZeroAndNull_afterClearAll() = runTest(testDispatcher) {
        // Req 4.3 + 7.5 integration: the Danger Zone clear path empties
        // the repo and the Settings screen re-renders the empty-state
        // placeholder.
        val (vm, repo, job) = newViewModelWithCollector()
        try {
            repo.put(sample("a", updatedAt = 100L))
            advanceUntilIdle()
            assertThat(vm.uiState.value.metadata.count).isEqualTo(1)

            repo.clearAll()
            advanceUntilIdle()

            assertThat(vm.uiState.value.metadata.count).isEqualTo(0)
            assertThat(vm.uiState.value.metadata.latestUpdatedAt).isNull()
        } finally {
            job.cancel()
        }
    }

    @Test
    fun refresh_reReadsLockAndStorage() = runTest(testDispatcher) {
        // Req 2.5, 3.5: returning from the system Settings re-reads the
        // non-reactive sources.
        var biometricStrong = BiometricManager.BIOMETRIC_ERROR_NONE_ENROLLED
        var biometricDevice = BiometricManager.BIOMETRIC_ERROR_NONE_ENROLLED
        var storageBytes = 100L
        val (vm, _, job) = newViewModelWithCollector(
            biometricStrongProvider = { biometricStrong },
            biometricDeviceProvider = { biometricDevice },
            storageBytesProvider = { storageBytes },
        )
        try {
            advanceUntilIdle()
            assertThat(vm.uiState.value.lockStatus).isEqualTo(DeviceLockStatus.NoLock)
            assertThat(vm.uiState.value.storageBytes).isEqualTo(100L)

            // Act: mutate the external state, then refresh.
            biometricStrong = BiometricManager.BIOMETRIC_SUCCESS
            biometricDevice = BiometricManager.BIOMETRIC_SUCCESS
            storageBytes = 4242L
            vm.refresh()
            advanceUntilIdle()

            assertThat(vm.uiState.value.lockStatus)
                .isEqualTo(DeviceLockStatus.BiometricAndDeviceCredential)
            assertThat(vm.uiState.value.storageBytes).isEqualTo(4242L)
        } finally {
            job.cancel()
        }
    }

    @Test
    fun uiState_initial_reportsNotEnabledAutofill_onUnconfiguredRobolectric() =
        runTest(testDispatcher) {
            // Req 2.1: Robolectric's AutofillManager is unconfigured, so
            // AutofillServiceStatus returns false. The ViewModel routes
            // false to AutofillStatus.NotEnabled.
            val (vm, _, job) = newViewModelWithCollector()
            try {
                advanceUntilIdle()
                assertThat(vm.uiState.value.autofillStatus).isEqualTo(AutofillStatus.NotEnabled)
            } finally {
                job.cancel()
            }
        }

    // ---- helpers --------------------------------------------------------

    /**
     * Builds a ViewModel + collector pair. Mirrors the pattern in
     * CredentialListViewModelTest so the WhileSubscribed-backed stateIn
     * stays active during the test. Callers cancel the returned [Job]
     * in `finally`.
     */
    private fun TestScope.newViewModelWithCollector(
        lockStatus: DeviceLockStatus = DeviceLockStatus.DeviceCredentialOnly,
        storageBytes: Long = 0L,
        appInfo: AppInfo = AppInfo(versionName = "1.0.0", versionCode = 1L),
        biometricStrongProvider: () -> Int = { biometricFor(lockStatus, strong = true) },
        biometricDeviceProvider: () -> Int = { biometricFor(lockStatus, strong = false) },
        storageBytesProvider: () -> Long = { storageBytes },
    ): Triple<SettingsViewModel, FakeCredentialRepository, Job> {
        val repo = FakeCredentialRepository()
        val biometric = mockk<BiometricManager>(relaxed = true)
        every {
            biometric.canAuthenticate(BiometricManager.Authenticators.BIOMETRIC_STRONG)
        } answers { biometricStrongProvider() }
        every {
            biometric.canAuthenticate(BiometricManager.Authenticators.DEVICE_CREDENTIAL)
        } answers { biometricDeviceProvider() }

        val ctx = ApplicationProvider.getApplicationContext<android.content.Context>()
        val vm = SettingsViewModel(
            appContext = ctx,
            observeMetadata = ObserveVaultMetadataUseCase(repo),
            getStorage = GetVaultStorageUsageUseCase(
                object : VaultStorageMeasurer(ctx) {
                    override suspend fun measureBytes(): Long = storageBytesProvider()
                },
            ),
            getLockStatus = GetDeviceLockStatusUseCase(biometric),
            appInfoProvider = object : AppInfoProvider(ctx) {
                override fun get(): AppInfo = appInfo
            },
        )
        // Drain uiState so the SharingStarted.WhileSubscribed pipeline
        // stays live for the duration of the test.
        val job = vm.uiState.onEach { /* keep alive */ }.launchIn(this)
        return Triple(vm, repo, job)
    }

    private fun biometricFor(status: DeviceLockStatus, strong: Boolean): Int = when (status) {
        DeviceLockStatus.BiometricAndDeviceCredential -> BiometricManager.BIOMETRIC_SUCCESS
        DeviceLockStatus.DeviceCredentialOnly ->
            if (strong) BiometricManager.BIOMETRIC_ERROR_NONE_ENROLLED
            else BiometricManager.BIOMETRIC_SUCCESS
        DeviceLockStatus.NoLock -> BiometricManager.BIOMETRIC_ERROR_NONE_ENROLLED
        DeviceLockStatus.UpdateRequired ->
            if (strong) BiometricManager.BIOMETRIC_ERROR_SECURITY_UPDATE_REQUIRED
            else BiometricManager.BIOMETRIC_SUCCESS
    }

    private fun sample(label: String, updatedAt: Long) = EncryptedCredentialRecord(
        id = CredentialId(0L),
        packageName = "com.example.$label",
        username = "user-$label",
        label = label,
        passwordCiphertext = byteArrayOf(0x01, 0x02, 0x03),
        passwordIv = ByteArray(12) { 0x10.toByte() },
        signatureSha256 = SigningHash(ByteArray(32) { 0x20.toByte() }),
        signatureCapturedAt = 1000L,
        createdAt = updatedAt,
        updatedAt = updatedAt,
    )
}
