package io.github.hitoshiichikawa.keynest.ui.edit

import com.google.common.truth.Truth.assertThat
import io.github.hitoshiichikawa.keynest.domain.model.CredentialId
import io.github.hitoshiichikawa.keynest.domain.model.CustomField
import io.github.hitoshiichikawa.keynest.domain.model.EncryptedCredentialRecord
import io.github.hitoshiichikawa.keynest.domain.usecase.DeleteCredentialUseCase
import io.github.hitoshiichikawa.keynest.domain.usecase.FakeCredentialRepository
import io.github.hitoshiichikawa.keynest.domain.usecase.FakeDetectedFieldRepository
import io.github.hitoshiichikawa.keynest.domain.usecase.ObserveRecentDetectedFieldsUseCase
import io.github.hitoshiichikawa.keynest.domain.usecase.SaveCredentialUseCase
import io.github.hitoshiichikawa.keynest.domain.usecase.StubAesGcmCipher
import io.github.hitoshiichikawa.keynest.domain.usecase.UpdateCredentialUseCase
import io.github.hitoshiichikawa.keynest.security.AesGcmCipher
import io.github.hitoshiichikawa.keynest.security.EncryptedBlob
import io.github.hitoshiichikawa.keynest.security.EncryptedCustomFieldsCodec
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
 * Mode.Edit customField decryption and editing behaviour, introduced
 * in Issue #73 Phase 1.5. Backs Req 1.1, 1.2, 1.5, 2.1-2.4, 3.1, 3.5,
 * 7.3, 10.1-10.6.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class CredentialEditViewModelEditModeCustomFieldsTest {

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
    fun loadInEditMode_decryptsAndExposesRows() = runTest(testDispatcher) {
        // Arrange: a credential whose customFieldsCiphertext was
        // produced by the same codec the ViewModel will use to decrypt.
        // StubAesGcmCipher's XOR transform is its own inverse so the
        // codec round-trip works under plain JVM.
        val repo = FakeCredentialRepository()
        val cipher = StubAesGcmCipher()
        val codec = EncryptedCustomFieldsCodec(cipher)
        val cfBlob = codec.encrypt(
            listOf(
                CustomField("memberId", "M-001"),
                CustomField("storeCode", "TKO"),
            ),
        )
        // Password must also decrypt successfully — the load path is
        // a single try/catch so a password failure would mask the
        // customField success. Use a non-zero ciphertext so the cipher
        // returns a sensible byte array.
        val pwBlob = cipher.encrypt("pw".toByteArray(Charsets.UTF_8))
        repo.put(makeRecord(cfBlob, pwBlob))
        val id = repo.snapshot().single().id.value
        val vm = newViewModel(repo, cipher, codec)

        // Act
        vm.load(id)
        advanceUntilIdle()

        // Assert: rows exposed in DB order, editable = true.
        val state = vm.customFields.value
        assertThat(state.editable).isTrue()
        assertThat(state.canAddMore).isTrue()
        assertThat(state.rows.map { it.fieldKey })
            .containsExactly("memberId", "storeCode").inOrder()
        assertThat(state.rows.map { it.value })
            .containsExactly("M-001", "TKO").inOrder()
    }

    @Test
    fun loadInEditMode_emptyCiphertext_yieldsEditableEmptyRows() = runTest(testDispatcher) {
        // Migration_2_3-default record: customFieldsCiphertext is a
        // zero-length array. The codec interprets that as "no fields"
        // and the ViewModel must still flip editable = true so the
        // user can add new rows (Req 1.5).
        val repo = FakeCredentialRepository()
        val cipher = StubAesGcmCipher()
        val codec = EncryptedCustomFieldsCodec(cipher)
        val pwBlob = cipher.encrypt("pw".toByteArray(Charsets.UTF_8))
        repo.put(
            makeRecord(
                customFieldsBlob = EncryptedBlob(iv = ByteArray(0), ciphertext = ByteArray(0)),
                passwordBlob = pwBlob,
            ),
        )
        val id = repo.snapshot().single().id.value
        val vm = newViewModel(repo, cipher, codec)

        vm.load(id)
        advanceUntilIdle()

        val state = vm.customFields.value
        assertThat(state.editable).isTrue()
        assertThat(state.rows).isEmpty()
        assertThat(state.canAddMore).isTrue()
    }

    @Test
    fun editModeReducers_addRemoveUpdate_workEndToEnd() = runTest(testDispatcher) {
        // After load(), the existing reducer methods (add / remove /
        // update*) should work without any Mode.New vs Mode.Edit
        // branching — NFR 5.1 mandates a single code path.
        val repo = FakeCredentialRepository()
        val cipher = StubAesGcmCipher()
        val codec = EncryptedCustomFieldsCodec(cipher)
        val cfBlob = codec.encrypt(listOf(CustomField("loadedKey", "loadedVal")))
        val pwBlob = cipher.encrypt("pw".toByteArray(Charsets.UTF_8))
        repo.put(makeRecord(cfBlob, pwBlob))
        val id = repo.snapshot().single().id.value
        val vm = newViewModel(repo, cipher, codec)
        vm.load(id)
        advanceUntilIdle()

        // Add a new row.
        vm.addCustomFieldRow()
        val afterAdd = vm.customFields.value.rows
        assertThat(afterAdd).hasSize(2)

        // Update the loaded row.
        val loadedId = afterAdd[0].rowId
        vm.updateCustomFieldKey(loadedId, "editedKey")
        vm.updateCustomFieldValue(loadedId, "editedVal")

        // Remove the newly added row.
        val newRowId = afterAdd[1].rowId
        vm.removeCustomFieldRow(newRowId)

        val finalRows = vm.customFields.value.rows
        assertThat(finalRows).hasSize(1)
        assertThat(finalRows.single().fieldKey).isEqualTo("editedKey")
        assertThat(finalRows.single().value).isEqualTo("editedVal")
    }

    @Test
    fun editModeSave_passesEditedListToUpdateUseCase() = runTest(testDispatcher) {
        // load(), edit one row, add another, save -> the persisted
        // ciphertext must reflect the edited list, not the load-time
        // list (Req 3.1 / 10.4).
        val repo = FakeCredentialRepository()
        val cipher = StubAesGcmCipher()
        val codec = EncryptedCustomFieldsCodec(cipher)
        val cfBlob = codec.encrypt(listOf(CustomField("memberId", "old")))
        val pwBlob = cipher.encrypt("pw".toByteArray(Charsets.UTF_8))
        repo.put(makeRecord(cfBlob, pwBlob))
        val id = repo.snapshot().single().id.value
        val vm = newViewModel(repo, cipher, codec)
        vm.load(id)
        advanceUntilIdle()

        val loadedRowId = vm.customFields.value.rows.single().rowId
        vm.updateCustomFieldValue(loadedRowId, "edited")
        vm.addCustomFieldRow()
        val newRowId = vm.customFields.value.rows[1].rowId
        vm.updateCustomFieldKey(newRowId, "storeCode")
        vm.updateCustomFieldValue(newRowId, "TKO")

        vm.save(
            existingId = id,
            packageName = "com.example.target",
            username = "alice",
            password = "pw".toCharArray(),
            label = "L",
        )
        advanceUntilIdle()

        assertThat(vm.state.value).isEqualTo(CredentialEditViewModel.State.Saved)
        val persisted = repo.findById(CredentialId(id))!!
        val decoded = codec.decrypt(
            EncryptedBlob(
                iv = persisted.customFieldsIv,
                ciphertext = persisted.customFieldsCiphertext,
            ),
        )
        assertThat(decoded.map { it.fieldKey })
            .containsExactly("memberId", "storeCode").inOrder()
        assertThat(decoded.map { it.value })
            .containsExactly("edited", "TKO").inOrder()
    }

    @Test
    fun editModeRoundTrip_preservesCustomFieldsValuesSemantically() = runTest(testDispatcher) {
        // Load -> save without editing -> reload -> same set
        // (Req 3.5 semantic equivalence under IV regeneration).
        val repo = FakeCredentialRepository()
        val cipher = StubAesGcmCipher()
        val codec = EncryptedCustomFieldsCodec(cipher)
        val original = listOf(
            CustomField("memberId", "M-001"),
            CustomField("storeCode", "TKO"),
        )
        val cfBlob = codec.encrypt(original)
        val pwBlob = cipher.encrypt("pw".toByteArray(Charsets.UTF_8))
        repo.put(makeRecord(cfBlob, pwBlob))
        val id = repo.snapshot().single().id.value
        val vm = newViewModel(repo, cipher, codec)
        vm.load(id)
        advanceUntilIdle()

        // No edits — just save.
        vm.save(
            existingId = id,
            packageName = "com.example.target",
            username = "alice",
            password = "pw".toCharArray(),
            label = "L",
        )
        advanceUntilIdle()

        // Reload via the same codec — the decoded list must content-
        // equal the original.
        val persisted = repo.findById(CredentialId(id))!!
        val decoded = codec.decrypt(
            EncryptedBlob(
                iv = persisted.customFieldsIv,
                ciphertext = persisted.customFieldsCiphertext,
            ),
        )
        assertThat(decoded).isEqualTo(original)
    }

    @Test
    fun editModeDecryptFailure_emitsErrorAndKeepsLocked() = runTest(testDispatcher) {
        // A throwing cipher routes the codec.decrypt call through the
        // ViewModel's catch block and lands on State.Error + locked
        // surfaces (Req 7.3).
        val repo = FakeCredentialRepository()
        val throwing = ThrowingAesGcmCipher()
        // The codec swallows JSON parse failures but propagates
        // cipher exceptions to the caller — exactly the ViewModel's
        // catch surface.
        val codec = EncryptedCustomFieldsCodec(throwing)
        repo.put(
            makeRecord(
                customFieldsBlob = EncryptedBlob(iv = ByteArray(12), ciphertext = byteArrayOf(1, 2)),
                passwordBlob = EncryptedBlob(iv = ByteArray(12), ciphertext = byteArrayOf(3, 4)),
            ),
        )
        val id = repo.snapshot().single().id.value
        val vm = newViewModel(repo, throwing, codec)

        vm.load(id)
        advanceUntilIdle()

        // State.Error emitted with the documented cause.
        val state = vm.state.value
        assertThat(state).isInstanceOf(CredentialEditViewModel.State.Error::class.java)
        assertThat((state as CredentialEditViewModel.State.Error).cause)
            .isEqualTo("decrypt_credential")

        // customField surface locked.
        val cfState = vm.customFields.value
        assertThat(cfState.editable).isFalse()
        assertThat(cfState.rows).isEmpty()
        assertThat(cfState.canAddMore).isFalse()
    }

    // ---- helpers -----------------------------------------------------------

    private fun makeRecord(
        customFieldsBlob: EncryptedBlob,
        passwordBlob: EncryptedBlob,
    ): EncryptedCredentialRecord = EncryptedCredentialRecord(
        id = CredentialId(0L),
        packageName = "com.example.target",
        username = "alice",
        label = "L",
        passwordCiphertext = passwordBlob.ciphertext,
        passwordIv = passwordBlob.iv,
        signatureSha256 = null,
        signatureCapturedAt = null,
        createdAt = 0L,
        updatedAt = 0L,
        customFieldsCiphertext = customFieldsBlob.ciphertext,
        customFieldsIv = customFieldsBlob.iv,
    )

    private fun newViewModel(
        repo: FakeCredentialRepository,
        cipher: AesGcmCipher,
        codec: EncryptedCustomFieldsCodec,
    ): CredentialEditViewModel {
        val sigResolver = mockk<PackageSignatureResolver>().also {
            every { it.resolveSha256(any()) } returns null
        }
        val save = SaveCredentialUseCase(repo, cipher, sigResolver, codec)
        val update = UpdateCredentialUseCase(repo, cipher, sigResolver, codec)
        val delete = DeleteCredentialUseCase(repo)
        val observeRecent = ObserveRecentDetectedFieldsUseCase(FakeDetectedFieldRepository())
        return CredentialEditViewModel(repo, save, update, delete, observeRecent, codec, cipher)
    }

    /**
     * Cipher that throws on every decrypt(), used to drive the
     * ViewModel's load-time catch path.
     */
    private class ThrowingAesGcmCipher : AesGcmCipher(
        // ServiceLocator wires a KeystoreKeyProvider but the stubbed
        // path never touches it. Use StubAesGcmCipher's idiom to keep
        // the constructor satisfied.
        StubAesGcmCipher().let { _ ->
            object : io.github.hitoshiichikawa.keynest.security.KeystoreKeyProvider() {
                override fun getOrCreateKey(): javax.crypto.SecretKey =
                    error("ThrowingAesGcmCipher should not invoke KeystoreKeyProvider.getOrCreateKey()")
            }
        },
    ) {
        override fun encrypt(plaintext: ByteArray): EncryptedBlob =
            error("ThrowingAesGcmCipher.encrypt should not be invoked by this test")

        override fun decrypt(blob: EncryptedBlob): ByteArray =
            throw RuntimeException("simulated AEAD failure")
    }
}
