package com.example.keynest.domain.usecase

import com.example.keynest.domain.model.CredentialId
import com.example.keynest.domain.model.EncryptedCredentialRecord
import com.example.keynest.domain.repository.CredentialRepository
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.test.runTest
import org.junit.Test

/**
 * Behaviour of [MarkCredentialUsedUseCase]. Issue #9 Req 3.2, NFR 1.3.
 */
class MarkCredentialUsedUseCaseTest {

    @Test
    fun invoke_stampsRepositoryWithProvidedNow() = runTest {
        // Arrange
        val repo = FakeCredentialRepository()
        val id = repo.save(blankRecord("alice"))
        val useCase = MarkCredentialUsedUseCase(repo, now = { 7777L })

        // Act
        val result = useCase(id)

        // Assert
        assertThat(result.isSuccess).isTrue()
        assertThat(repo.findById(id)!!.lastUsedAt).isEqualTo(7777L)
    }

    @Test
    fun invoke_returnsSuccess_evenWhenIdIsUnknown() = runTest {
        // Req 3.2: race against delete must not throw. Fake / Room
        // markUsed are both silent for missing ids, so the use case
        // returns success.
        val repo = FakeCredentialRepository()
        val useCase = MarkCredentialUsedUseCase(repo, now = { 1L })

        val result = useCase(CredentialId(9999L))

        assertThat(result.isSuccess).isTrue()
    }

    @Test
    fun invoke_returnsStorageFailure_withClassNameReason_whenRepoThrows() = runTest {
        // NFR 1.3: failure must not contain plaintext or stack details.
        val delegate = FakeCredentialRepository()
        val throwingRepo = object : CredentialRepository by delegate {
            override suspend fun markUsed(id: CredentialId, timestamp: Long) {
                throw java.io.IOException("disk full DETAILED")
            }
        }
        val useCase = MarkCredentialUsedUseCase(throwingRepo, now = { 1L })

        val result = useCase(CredentialId(1L))

        assertThat(result.isFailure).isTrue()
        val failure = result.exceptionOrNull()!!
        assertThat(failure).isInstanceOf(MarkUsedFailure.Storage::class.java)
        val storage = failure as MarkUsedFailure.Storage
        // Reason is the simple class name only - no caller-supplied detail.
        assertThat(storage.reason).isEqualTo("IOException")
        assertThat(storage.message ?: "").doesNotContain("DETAILED")
    }

    private fun blankRecord(username: String) = EncryptedCredentialRecord(
        id = CredentialId(0L),
        packageName = "com.example.$username",
        username = username,
        label = "Label-$username",
        passwordCiphertext = byteArrayOf(1),
        passwordIv = ByteArray(12),
        signatureSha256 = null,
        signatureCapturedAt = null,
        createdAt = 0L,
        updatedAt = 0L,
    )
}
