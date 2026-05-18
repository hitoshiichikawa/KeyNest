package io.github.hitoshiichikawa.keynest.ui

import com.google.common.truth.Truth.assertThat
import io.github.hitoshiichikawa.keynest.domain.usecase.DeleteCredentialUseCase
import io.github.hitoshiichikawa.keynest.domain.usecase.FakeCredentialRepository
import io.github.hitoshiichikawa.keynest.domain.usecase.FakeDetectedFieldRepository
import io.github.hitoshiichikawa.keynest.domain.usecase.ObserveRecentDetectedFieldsUseCase
import io.github.hitoshiichikawa.keynest.domain.usecase.SaveCredentialUseCase
import io.github.hitoshiichikawa.keynest.domain.usecase.StubAesGcmCipher
import io.github.hitoshiichikawa.keynest.domain.usecase.UpdateCredentialUseCase
import io.github.hitoshiichikawa.keynest.security.EncryptedCustomFieldsCodec
import io.github.hitoshiichikawa.keynest.ui.edit.CredentialEditViewModel
import io.github.hitoshiichikawa.keynest.util.PackageSignatureResolver
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
 * Reducer behaviour of the customFields editor on [CredentialEditViewModel].
 * Issue #66 Phase 1. Backs Req 6.5 (add / remove / 10-cap / blank silent
 * drop / canAddMore).
 */
@OptIn(ExperimentalCoroutinesApi::class)
class CredentialEditViewModelCustomFieldsTest {

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
    fun addCustomFieldRow_appendsEmptyRow_andAssignsUniqueId() {
        val vm = newViewModel()

        vm.addCustomFieldRow()
        vm.addCustomFieldRow()

        val rows = vm.customFields.value.rows
        assertThat(rows).hasSize(2)
        assertThat(rows[0].fieldKey).isEmpty()
        assertThat(rows[0].value).isEmpty()
        assertThat(rows[0].rowId).isNotEqualTo(rows[1].rowId)
    }

    @Test
    fun addCustomFieldRow_capsAt10_andCanAddMoreFlipsFalse() {
        val vm = newViewModel()

        repeat(11) { vm.addCustomFieldRow() }

        assertThat(vm.customFields.value.rows).hasSize(10)
        assertThat(vm.customFields.value.canAddMore).isFalse()
    }

    @Test
    fun updateCustomFieldKeyAndValue_mutateOnlyTargetRow() {
        val vm = newViewModel()
        vm.addCustomFieldRow()
        vm.addCustomFieldRow()
        val ids = vm.customFields.value.rows.map { it.rowId }

        vm.updateCustomFieldKey(ids[0], "memberId")
        vm.updateCustomFieldValue(ids[1], "TKO-001")

        val rows = vm.customFields.value.rows
        assertThat(rows[0].fieldKey).isEqualTo("memberId")
        assertThat(rows[0].value).isEmpty()
        assertThat(rows[1].fieldKey).isEmpty()
        assertThat(rows[1].value).isEqualTo("TKO-001")
    }

    @Test
    fun removeCustomFieldRow_dropsTargetAndPreservesOthers() {
        val vm = newViewModel()
        vm.addCustomFieldRow()
        vm.addCustomFieldRow()
        vm.addCustomFieldRow()
        val ids = vm.customFields.value.rows.map { it.rowId }
        vm.updateCustomFieldKey(ids[1], "keep")

        vm.removeCustomFieldRow(ids[0])

        val rows = vm.customFields.value.rows
        assertThat(rows).hasSize(2)
        assertThat(rows.map { it.rowId }).containsExactly(ids[1], ids[2]).inOrder()
        assertThat(rows[0].fieldKey).isEqualTo("keep")
    }

    @Test
    fun save_dropsRowsWithBlankFieldKey_andPersistsRest() = runTest(testDispatcher) {
        val repo = FakeCredentialRepository()
        val cipher = StubAesGcmCipher()
        val codec = EncryptedCustomFieldsCodec(cipher)
        val vm = newViewModel(repo, cipher, codec)
        vm.addCustomFieldRow()
        vm.addCustomFieldRow()
        vm.addCustomFieldRow()
        val ids = vm.customFields.value.rows.map { it.rowId }
        // Row 0: blank fieldKey -> silently dropped (Req 3.4)
        vm.updateCustomFieldKey(ids[0], "  ")
        vm.updateCustomFieldValue(ids[0], "should-be-dropped")
        // Row 1: real entry
        vm.updateCustomFieldKey(ids[1], "memberId")
        vm.updateCustomFieldValue(ids[1], "M-9999")
        // Row 2: real entry with empty value (still saved per spec - blank
        // fieldKey is the only silent-drop trigger).
        vm.updateCustomFieldKey(ids[2], "storeCode")

        vm.save(
            existingId = null,
            packageName = "com.example.target",
            username = "alice",
            password = "pw".toCharArray(),
            label = "L",
        )
        advanceUntilIdle()

        assertThat(vm.state.value).isEqualTo(CredentialEditViewModel.State.Saved)
        val record = repo.snapshot().single()
        val decoded = codec.decrypt(
            io.github.hitoshiichikawa.keynest.security.EncryptedBlob(
                iv = record.customFieldsIv,
                ciphertext = record.customFieldsCiphertext,
            ),
        )
        assertThat(decoded.map { it.fieldKey }).containsExactly("memberId", "storeCode").inOrder()
        assertThat(decoded.map { it.value }).containsExactly("M-9999", "").inOrder()
    }

    @Test
    fun load_existingCredential_marksCustomFieldsSectionReadOnly() = runTest(testDispatcher) {
        // design.md §9.3 暫定: in edit mode the customFields section is
        // read-only for Phase 1. canAddMore must be false.
        val repo = FakeCredentialRepository()
        repo.put(
            io.github.hitoshiichikawa.keynest.domain.model.EncryptedCredentialRecord(
                id = io.github.hitoshiichikawa.keynest.domain.model.CredentialId(0L),
                packageName = "com.example.target",
                username = "alice",
                label = "L",
                passwordCiphertext = byteArrayOf(1),
                passwordIv = ByteArray(12),
                signatureSha256 = null,
                signatureCapturedAt = null,
                createdAt = 0L,
                updatedAt = 0L,
            ),
        )
        val id = repo.snapshot().single().id.value
        val vm = newViewModel(repo)

        vm.load(id)
        advanceUntilIdle()

        assertThat(vm.customFields.value.editable).isFalse()
        assertThat(vm.customFields.value.canAddMore).isFalse()
        assertThat(vm.customFields.value.rows).isEmpty()
    }

    private fun newViewModel(
        repo: FakeCredentialRepository = FakeCredentialRepository(),
        cipher: StubAesGcmCipher = StubAesGcmCipher(),
        codec: EncryptedCustomFieldsCodec = EncryptedCustomFieldsCodec(cipher),
    ): CredentialEditViewModel {
        val sigResolver = mockk<PackageSignatureResolver>().also { every { it.resolveSha256(any()) } returns null }
        val save = SaveCredentialUseCase(repo, cipher, sigResolver, codec)
        val update = UpdateCredentialUseCase(repo, cipher, sigResolver, codec)
        val delete = DeleteCredentialUseCase(repo)
        val observeRecent = ObserveRecentDetectedFieldsUseCase(FakeDetectedFieldRepository())
        return CredentialEditViewModel(repo, save, update, delete, observeRecent, codec, cipher)
    }
}
