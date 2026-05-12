package com.example.keynest.ui.edit

import com.example.keynest.domain.model.CredentialId
import com.example.keynest.domain.model.EncryptedCredentialRecord
import com.example.keynest.domain.model.SigningHash
import com.example.keynest.domain.usecase.FakeCredentialRepository
import com.example.keynest.domain.usecase.SaveCredentialUseCase
import com.example.keynest.domain.usecase.StubAesGcmCipher
import com.example.keynest.domain.usecase.UpdateCredentialUseCase
import com.example.keynest.util.PackageSignatureResolver
import com.google.common.truth.Truth.assertThat
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Before
import org.junit.Test

/**
 * Advanced-details state exposed by [CredentialEditViewModel] (Issue #14).
 *
 * Backs requirements:
 * - 1.1 (initial collapsed)
 * - 1.2 (toggle expanded <-> collapsed)
 * - 1.5 (toggle state survives intra-Activity reloads -- proxy: ViewModel
 *   keeps it across .load() calls)
 * - 2.1, 2.2, 2.4 (createdAt + updatedAt both exposed even if equal)
 * - 2.3 (new mode -> timestamps null)
 * - 3.1, 3.4 (sha256 hex full 64 chars / null branch)
 * - 4.1, 4.2 (signature captured timestamp / null branch)
 * - 5.1, 5.2, 5.3, 5.4 (credential ID toggle + initial hidden)
 * - 5.5 (new mode -> credential ID is null)
 */
@OptIn(ExperimentalCoroutinesApi::class)
class CredentialEditAdvancedStateTest {

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
    fun advancedDetails_initialValue_isCollapsedAndNewMode() = runTest(testDispatcher) {
        // Arrange + Act
        val vm = newViewModel(repo = FakeCredentialRepository())

        // Assert: Req 1.1 (collapsed) + Req 5.2 (id hidden) + Req 2.3 / 5.5 (new mode)
        val ad = vm.advancedDetails.value
        assertThat(ad.expanded).isFalse()
        assertThat(ad.credentialIdVisible).isFalse()
        assertThat(ad.mode).isEqualTo(CredentialEditViewModel.Mode.New)
        assertThat(ad.credentialId).isNull()
        assertThat(ad.createdAt).isNull()
        assertThat(ad.updatedAt).isNull()
        assertThat(ad.signatureSha256Hex).isNull()
        assertThat(ad.signatureCapturedAt).isNull()
    }

    @Test
    fun toggleAdvancedExpanded_flipsBetweenCollapsedAndExpanded() = runTest(testDispatcher) {
        // Arrange
        val vm = newViewModel(repo = FakeCredentialRepository())

        // Act + Assert Req 1.2 (toggle on a UI tap)
        vm.toggleAdvancedExpanded()
        assertThat(vm.advancedDetails.value.expanded).isTrue()
        vm.toggleAdvancedExpanded()
        assertThat(vm.advancedDetails.value.expanded).isFalse()
    }

    @Test
    fun toggleCredentialIdVisible_flipsBetweenHiddenAndVisible() = runTest(testDispatcher) {
        // Arrange
        val vm = newViewModel(repo = FakeCredentialRepository())

        // Act + Assert Req 5.3 / 5.4
        vm.toggleCredentialIdVisible()
        assertThat(vm.advancedDetails.value.credentialIdVisible).isTrue()
        vm.toggleCredentialIdVisible()
        assertThat(vm.advancedDetails.value.credentialIdVisible).isFalse()
    }

    @Test
    fun load_inEditMode_populatesAllAdvancedFields_andPreservesTogglestate() = runTest(testDispatcher) {
        // Arrange: a credential with signature data
        val repo = FakeCredentialRepository()
        val hash = SigningHash(ByteArray(32) { 0x42.toByte() })
        repo.put(
            EncryptedCredentialRecord(
                id = CredentialId(0L),
                packageName = "com.example.target",
                username = "alice",
                label = "L",
                passwordCiphertext = byteArrayOf(1),
                passwordIv = ByteArray(12),
                signatureSha256 = hash,
                signatureCapturedAt = 1_700_000_000_000L,
                createdAt = 1_500_000_000_000L,
                updatedAt = 1_600_000_000_000L,
            ),
        )
        val id = repo.snapshot().single().id.value
        val vm = newViewModel(repo)
        // Expand + reveal ID first; subsequent load() must preserve them
        // (Req 1.5: state persists for the Edit Activity lifetime).
        vm.toggleAdvancedExpanded()
        vm.toggleCredentialIdVisible()

        // Act
        val record = vm.load(id)
        advanceUntilIdle()

        // Assert
        assertThat(record).isNotNull()
        val ad = vm.advancedDetails.value
        assertThat(ad.mode).isEqualTo(CredentialEditViewModel.Mode.Edit)
        assertThat(ad.credentialId).isEqualTo(id)
        assertThat(ad.createdAt).isEqualTo(1_500_000_000_000L)
        assertThat(ad.updatedAt).isEqualTo(1_600_000_000_000L)
        assertThat(ad.signatureCapturedAt).isEqualTo(1_700_000_000_000L)
        // Req 3.1: full 64-char lowercase hex
        assertThat(ad.signatureSha256Hex).hasLength(64)
        assertThat(ad.signatureSha256Hex).matches("[0-9a-f]{64}")
        // Req 1.5 toggle preservation
        assertThat(ad.expanded).isTrue()
        assertThat(ad.credentialIdVisible).isTrue()
    }

    @Test
    fun load_inEditMode_whenSignatureMissing_setsHexAndCapturedAtToNull() = runTest(testDispatcher) {
        // Arrange: signature_sha256 = null (target app uninstalled at save time)
        val repo = FakeCredentialRepository()
        repo.put(
            EncryptedCredentialRecord(
                id = CredentialId(0L),
                packageName = "com.example.target",
                username = "alice",
                label = "L",
                passwordCiphertext = byteArrayOf(1),
                passwordIv = ByteArray(12),
                signatureSha256 = null,
                signatureCapturedAt = null,
                createdAt = 1_500_000_000_000L,
                updatedAt = 1_500_000_000_000L,
            ),
        )
        val id = repo.snapshot().single().id.value
        val vm = newViewModel(repo)

        // Act
        vm.load(id)
        advanceUntilIdle()

        // Assert: Req 3.4 / 4.2 -- UI uses null to render "not captured"
        val ad = vm.advancedDetails.value
        assertThat(ad.signatureSha256Hex).isNull()
        assertThat(ad.signatureCapturedAt).isNull()
        // Req 2.4: even if createdAt == updatedAt, both are still exposed
        assertThat(ad.createdAt).isEqualTo(ad.updatedAt)
        assertThat(ad.createdAt).isNotNull()
    }

    @Test
    fun toggleAdvancedExpanded_doesNotResetTransientFormState_norTriggerSave() = runTest(testDispatcher) {
        // Req 6.2: toggling the advanced section MUST NOT clear the user's
        // in-progress form input. The Activity owns the form text via its
        // TextInputEditText widgets, so the ViewModel-level guarantee we
        // can pin here is "toggling doesn't transition to Saving / Saved /
        // FieldError". An Activity rendering test would be required to
        // verify EditText survival.
        val repo = FakeCredentialRepository()
        val vm = newViewModel(repo)
        val before = vm.state.value

        vm.toggleAdvancedExpanded()
        vm.toggleCredentialIdVisible()

        assertThat(vm.state.value).isSameInstanceAs(before)
    }

    @Test
    fun load_withNonexistentId_keepsNewModeAndNullFields() = runTest(testDispatcher) {
        // Arrange: empty repository, look up an id that doesn't exist
        val repo = FakeCredentialRepository()
        val vm = newViewModel(repo)

        // Act
        val record = vm.load(credentialId = 999L)
        advanceUntilIdle()

        // Assert: no record -> stays as new-mode metadata so caller knows
        // not to render edit-only rows (Req 6 / Req 2.3 safety net).
        assertThat(record).isNull()
        val ad = vm.advancedDetails.value
        assertThat(ad.mode).isEqualTo(CredentialEditViewModel.Mode.New)
        assertThat(ad.credentialId).isNull()
        assertThat(ad.createdAt).isNull()
        assertThat(ad.updatedAt).isNull()
        assertThat(ad.signatureSha256Hex).isNull()
        assertThat(ad.signatureCapturedAt).isNull()
    }

    private fun newViewModel(repo: FakeCredentialRepository): CredentialEditViewModel {
        val sigResolver = mockk<PackageSignatureResolver>().also {
            every { it.resolveSha256(any()) } returns null
        }
        val cipher = StubAesGcmCipher()
        val save = SaveCredentialUseCase(repo, cipher, sigResolver)
        val update = UpdateCredentialUseCase(repo, cipher, sigResolver)
        return CredentialEditViewModel(repo, save, update)
    }
}
