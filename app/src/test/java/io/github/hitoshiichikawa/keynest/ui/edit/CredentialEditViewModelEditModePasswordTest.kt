package io.github.hitoshiichikawa.keynest.ui.edit

import com.google.common.truth.Truth.assertThat
import io.github.hitoshiichikawa.keynest.domain.model.CredentialId
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
import io.mockk.coVerify
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
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
import io.github.hitoshiichikawa.keynest.domain.usecase.UpdateCredentialInput

/**
 * Mode.Edit password decryption and dirty-judge behaviour, introduced
 * in Issue #73 Phase 1.5. Backs Req 4.1, 4.5, 4.6, 6.1-6.5,
 * 10.5-10.10.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class CredentialEditViewModelEditModePasswordTest {

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
    fun loadInEditMode_decryptsPasswordToInitialPassword() = runTest(testDispatcher) {
        val repo = FakeCredentialRepository()
        val cipher = StubAesGcmCipher()
        val codec = EncryptedCustomFieldsCodec(cipher)
        val pwBlob = cipher.encrypt("hunter2".toByteArray(Charsets.UTF_8))
        repo.put(makeRecord(passwordBlob = pwBlob, customFieldsBlob = codec.encrypt(emptyList())))
        val id = repo.snapshot().single().id.value
        val vm = newViewModel(repo, cipher, codec)

        vm.load(id)
        advanceUntilIdle()

        assertThat(vm.editState.value.initialPassword).isEqualTo("hunter2")
    }

    @Test
    fun editModeSave_unchangedPassword_passesNullToUpdateInput() = runTest(testDispatcher) {
        // The user opens the editor, never touches the password
        // field, and submits. The dirty judge must build the
        // UpdateCredentialInput with newPassword = null so the
        // existing ciphertext is left untouched (Req 6.1).
        val repo = FakeCredentialRepository()
        val cipher = StubAesGcmCipher()
        val codec = EncryptedCustomFieldsCodec(cipher)
        val pwBlob = cipher.encrypt("hunter2".toByteArray(Charsets.UTF_8))
        val cfBlob = codec.encrypt(emptyList())
        repo.put(makeRecord(passwordBlob = pwBlob, customFieldsBlob = cfBlob))
        val id = repo.snapshot().single().id.value

        val (vm, updateUseCase) = newViewModelWithMockedUpdate(repo, cipher, codec)
        vm.load(id)
        advanceUntilIdle()

        // Submit the same password unchanged.
        vm.save(
            existingId = id,
            packageName = "com.example.target",
            username = "alice",
            password = "hunter2".toCharArray(),
            label = "L",
        )
        advanceUntilIdle()

        // The update use case received UpdateCredentialInput with
        // newPassword = null.
        val captured = slot<UpdateCredentialInput>()
        coVerify { updateUseCase.invoke(capture(captured)) }
        assertThat(captured.captured.newPassword).isNull()
    }

    @Test
    fun editModeSave_changedPassword_passesNewCharArrayToUpdateInput() = runTest(testDispatcher) {
        // The user replaces the password; the new value is forwarded
        // as a CharArray so the use case can re-encrypt (Req 6.2).
        val repo = FakeCredentialRepository()
        val cipher = StubAesGcmCipher()
        val codec = EncryptedCustomFieldsCodec(cipher)
        val pwBlob = cipher.encrypt("oldPw".toByteArray(Charsets.UTF_8))
        val cfBlob = codec.encrypt(emptyList())
        repo.put(makeRecord(passwordBlob = pwBlob, customFieldsBlob = cfBlob))
        val id = repo.snapshot().single().id.value

        val (vm, updateUseCase) = newViewModelWithMockedUpdate(repo, cipher, codec)
        vm.load(id)
        advanceUntilIdle()

        vm.save(
            existingId = id,
            packageName = "com.example.target",
            username = "alice",
            password = "newPw!".toCharArray(),
            label = "L",
        )
        advanceUntilIdle()

        val captured = slot<UpdateCredentialInput>()
        coVerify { updateUseCase.invoke(capture(captured)) }
        // The use case zero-fills the CharArray after consuming it,
        // so we only verify the content at the moment of capture by
        // comparing concatToString() before mockk hands control back
        // -- which here is fine because the mock returns immediately.
        assertThat(captured.captured.newPassword).isNotNull()
        // contentEquals over slot.captured semantics: the slot keeps
        // a reference to the CharArray. The use case zero-fill runs
        // inside the real use case which we mocked out, so the
        // CharArray is still intact here.
        assertThat(String(captured.captured.newPassword!!)).isEqualTo("newPw!")
    }

    @Test
    fun editModeSave_blankPassword_emitsValidationErrorAndAborts() = runTest(testDispatcher) {
        // A blank submission in Mode.Edit is treated as "the user
        // wiped the field and tried to save an empty value" — which
        // is rejected by an inline FieldError (Req 6.3 / 10.10). No
        // update use case call is made.
        val repo = FakeCredentialRepository()
        val cipher = StubAesGcmCipher()
        val codec = EncryptedCustomFieldsCodec(cipher)
        val pwBlob = cipher.encrypt("hunter2".toByteArray(Charsets.UTF_8))
        val cfBlob = codec.encrypt(emptyList())
        repo.put(makeRecord(passwordBlob = pwBlob, customFieldsBlob = cfBlob))
        val id = repo.snapshot().single().id.value

        val (vm, updateUseCase) = newViewModelWithMockedUpdate(repo, cipher, codec)
        vm.load(id)
        advanceUntilIdle()

        vm.save(
            existingId = id,
            packageName = "com.example.target",
            username = "alice",
            password = CharArray(0),
            label = "L",
        )
        advanceUntilIdle()

        val state = vm.state.value
        assertThat(state).isInstanceOf(CredentialEditViewModel.State.FieldError::class.java)
        val err = state as CredentialEditViewModel.State.FieldError
        assertThat(err.field).isEqualTo(CredentialEditViewModel.Field.Password)
        assertThat(err.kind).isEqualTo(CredentialEditViewModel.ErrorKind.Blank)
        coVerify(exactly = 0) { updateUseCase.invoke(any()) }
    }

    @Test
    fun editModeRoundTrip_preservesPasswordSemantically() = runTest(testDispatcher) {
        // load -> unedited save -> reload -> same plaintext (Req 10.5
        // password side). Because newPassword = null, the use case
        // leaves the existing ciphertext untouched, so the decrypted
        // plaintext after save remains identical.
        val repo = FakeCredentialRepository()
        val cipher = StubAesGcmCipher()
        val codec = EncryptedCustomFieldsCodec(cipher)
        val pwBlob = cipher.encrypt("hunter2".toByteArray(Charsets.UTF_8))
        repo.put(makeRecord(passwordBlob = pwBlob, customFieldsBlob = codec.encrypt(emptyList())))
        val id = repo.snapshot().single().id.value
        val vm = newViewModel(repo, cipher, codec)
        vm.load(id)
        advanceUntilIdle()

        vm.save(
            existingId = id,
            packageName = "com.example.target",
            username = "alice",
            password = "hunter2".toCharArray(),
            label = "L",
        )
        advanceUntilIdle()

        val persisted = repo.findById(CredentialId(id))!!
        val plaintext = String(
            cipher.decrypt(
                EncryptedBlob(iv = persisted.passwordIv, ciphertext = persisted.passwordCiphertext),
            ),
            Charsets.UTF_8,
        )
        assertThat(plaintext).isEqualTo("hunter2")
    }

    @Test
    fun editModeDecryptFailure_emitsErrorAndKeepsPasswordNull() = runTest(testDispatcher) {
        // cipher.decrypt throws -> ViewModel catches, emits State.Error,
        // initialPassword stays null (Req 4.5 / 10.6 password side).
        val repo = FakeCredentialRepository()
        val throwing = ThrowingAesGcmCipher()
        val codec = EncryptedCustomFieldsCodec(throwing)
        repo.put(
            makeRecord(
                passwordBlob = EncryptedBlob(iv = ByteArray(12), ciphertext = byteArrayOf(1, 2)),
                customFieldsBlob = EncryptedBlob(iv = ByteArray(0), ciphertext = ByteArray(0)),
            ),
        )
        val id = repo.snapshot().single().id.value
        val vm = newViewModel(repo, throwing, codec)

        vm.load(id)
        advanceUntilIdle()

        val state = vm.state.value
        assertThat(state).isInstanceOf(CredentialEditViewModel.State.Error::class.java)
        assertThat((state as CredentialEditViewModel.State.Error).cause)
            .isEqualTo("decrypt_credential")
        assertThat(vm.editState.value.initialPassword).isNull()
    }

    // ---- helpers -----------------------------------------------------------

    private fun makeRecord(
        passwordBlob: EncryptedBlob,
        customFieldsBlob: EncryptedBlob,
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
     * Variant that swaps in a MockK-driven UpdateCredentialUseCase so
     * the test can assert on the exact UpdateCredentialInput passed
     * to it (newPassword field in particular).
     */
    private fun newViewModelWithMockedUpdate(
        repo: FakeCredentialRepository,
        cipher: AesGcmCipher,
        codec: EncryptedCustomFieldsCodec,
    ): Pair<CredentialEditViewModel, UpdateCredentialUseCase> {
        val sigResolver = mockk<PackageSignatureResolver>().also {
            every { it.resolveSha256(any()) } returns null
        }
        val save = SaveCredentialUseCase(repo, cipher, sigResolver, codec)
        val update = mockk<UpdateCredentialUseCase>()
        coEvery { update.invoke(any()) } returns Result.success(Unit)
        val delete = DeleteCredentialUseCase(repo)
        val observeRecent = ObserveRecentDetectedFieldsUseCase(FakeDetectedFieldRepository())
        val vm = CredentialEditViewModel(repo, save, update, delete, observeRecent, codec, cipher)
        return vm to update
    }

    /**
     * Cipher that throws on every decrypt(), used to drive the
     * ViewModel's load-time catch path.
     */
    private class ThrowingAesGcmCipher : AesGcmCipher(
        object : io.github.hitoshiichikawa.keynest.security.KeystoreKeyProvider() {
            override fun getOrCreateKey(): javax.crypto.SecretKey =
                error("ThrowingAesGcmCipher should not invoke KeystoreKeyProvider.getOrCreateKey()")
        },
    ) {
        override fun encrypt(plaintext: ByteArray): EncryptedBlob =
            error("ThrowingAesGcmCipher.encrypt should not be invoked by this test")

        override fun decrypt(blob: EncryptedBlob): ByteArray =
            throw RuntimeException("simulated AEAD failure")
    }
}
