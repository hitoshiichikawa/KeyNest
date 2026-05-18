package io.github.hitoshiichikawa.keynest.domain.usecase

import io.github.hitoshiichikawa.keynest.domain.model.CredentialId
import io.github.hitoshiichikawa.keynest.domain.model.EncryptedCredentialRecord
import io.github.hitoshiichikawa.keynest.domain.model.SigningHash
import io.github.hitoshiichikawa.keynest.util.PackageSignatureResolver
import com.google.common.truth.Truth.assertThat
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Test

/**
 * Behaviour of [UpdateCredentialUseCase]. Covers Req 1.5, 2.3.
 */
class UpdateCredentialUseCaseTest {

    private val repo = FakeCredentialRepository()
    private val cipher = StubAesGcmCipher()
    private val sigResolver = mockk<PackageSignatureResolver>()

    private fun seedCredential(): CredentialId {
        val rec = EncryptedCredentialRecord(
            id = CredentialId(0L),
            packageName = "com.example.target",
            username = "alice",
            label = "Example",
            passwordCiphertext = byteArrayOf(1, 2, 3),
            passwordIv = ByteArray(12) { 1 },
            signatureSha256 = SigningHash.ofSha256("OLD".toByteArray()),
            signatureCapturedAt = 100L,
            createdAt = 100L,
            updatedAt = 100L,
        )
        repo.put(rec)
        return repo.snapshot().single().id
    }

    @Test
    fun invoke_updatesSignatureHashOnEveryCall() = runTest {
        // Req 2.3: re-resolve current hash on update.
        val id = seedCredential()
        val newHash = SigningHash.ofSha256("NEW".toByteArray())
        every { sigResolver.resolveSha256("com.example.target") } returns newHash
        val useCase = UpdateCredentialUseCase(repo, cipher, sigResolver) { 200L }

        val result = useCase(
            UpdateCredentialInput(
                id = id,
                packageName = "com.example.target",
                username = "alice",
                label = "Example",
                newPassword = null,
            ),
        )

        assertThat(result.isSuccess).isTrue()
        val updated = repo.snapshot().single()
        assertThat(updated.signatureSha256).isEqualTo(newHash)
        assertThat(updated.signatureCapturedAt).isEqualTo(200L)
        assertThat(updated.updatedAt).isEqualTo(200L)
        // createdAt is preserved
        assertThat(updated.createdAt).isEqualTo(100L)
    }

    @Test
    fun invoke_clearsSignature_whenAppNotInstalledAnymore() = runTest {
        // Req 2.3: if PackageManager returns null at update time, the
        // signature must be cleared (the credential becomes "no-match").
        val id = seedCredential()
        every { sigResolver.resolveSha256("com.example.target") } returns null
        val useCase = UpdateCredentialUseCase(repo, cipher, sigResolver) { 300L }

        useCase(UpdateCredentialInput(id, "com.example.target", "alice", "Example", null))

        val updated = repo.snapshot().single()
        assertThat(updated.signatureSha256).isNull()
        assertThat(updated.signatureCapturedAt).isNull()
    }

    @Test
    fun invoke_reencryptsPassword_whenNewPasswordProvided() = runTest {
        // Req 1.2: never persist plaintext.
        val id = seedCredential()
        every { sigResolver.resolveSha256(any()) } returns null
        val useCase = UpdateCredentialUseCase(repo, cipher, sigResolver) { 200L }
        val newPw = "fresh-secret".toCharArray()

        useCase(UpdateCredentialInput(id, "com.example.target", "alice", "Example", newPw))

        val updated = repo.snapshot().single()
        assertThat(String(updated.passwordCiphertext, Charsets.UTF_8)).doesNotContain("fresh-secret")
        // CharArray must be wiped
        assertThat(newPw).isEqualTo(CharArray(newPw.size) { ' ' })
    }

    @Test
    fun invoke_keepsExistingPasswordCiphertext_whenNewPasswordIsNull() = runTest {
        // Editing metadata only.
        val id = seedCredential()
        every { sigResolver.resolveSha256(any()) } returns null
        val useCase = UpdateCredentialUseCase(repo, cipher, sigResolver) { 200L }
        val originalCiphertext = repo.snapshot().single().passwordCiphertext.copyOf()
        val originalIv = repo.snapshot().single().passwordIv.copyOf()

        useCase(UpdateCredentialInput(id, "com.example.target", "alice-renamed", "Example", null))

        val updated = repo.snapshot().single()
        assertThat(updated.passwordCiphertext).isEqualTo(originalCiphertext)
        assertThat(updated.passwordIv).isEqualTo(originalIv)
        assertThat(updated.username).isEqualTo("alice-renamed")
    }

    @Test
    fun invoke_failsValidation_whenPackageNameMissingOrInvalid() = runTest {
        val id = seedCredential()
        val useCase = UpdateCredentialUseCase(repo, cipher, sigResolver)

        val blank = useCase(UpdateCredentialInput(id, "  ", "alice", "L", null))
        val invalid = useCase(UpdateCredentialInput(id, "noDots", "alice", "L", null))

        assertThat(blank.exceptionOrNull()).isInstanceOf(UpdateFailure.PackageNameBlank::class.java)
        assertThat(invalid.exceptionOrNull()).isInstanceOf(UpdateFailure.PackageNameInvalid::class.java)
    }

    @Test
    fun invoke_returnsNotFound_whenIdIsUnknown() = runTest {
        every { sigResolver.resolveSha256(any()) } returns null
        val useCase = UpdateCredentialUseCase(repo, cipher, sigResolver)
        val r = useCase(UpdateCredentialInput(CredentialId(9999L), "com.example.target", "alice", "L", null))
        assertThat(r.exceptionOrNull()).isInstanceOf(UpdateFailure.NotFound::class.java)
    }
}
