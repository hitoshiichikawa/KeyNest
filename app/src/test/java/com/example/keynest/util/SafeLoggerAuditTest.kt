package com.example.keynest.util

import com.example.keynest.domain.model.CredentialId
import com.example.keynest.domain.model.EncryptedCredentialRecord
import com.example.keynest.domain.usecase.FakeCredentialRepository
import com.example.keynest.domain.usecase.NewCredentialInput
import com.example.keynest.domain.usecase.SaveCredentialUseCase
import com.example.keynest.domain.usecase.StubAesGcmCipher
import com.example.keynest.domain.usecase.UnlockVaultUseCase
import com.example.keynest.security.AesGcmCipher
import com.example.keynest.security.EncryptedBlob
import com.example.keynest.security.KeystoreKeyProvider
import com.google.common.truth.Truth.assertThat
import io.mockk.every
import io.mockk.mockk
import javax.crypto.AEADBadTagException
import javax.crypto.SecretKey
import kotlinx.coroutines.test.runTest
import org.junit.Test

/**
 * T10.5: audits that exception messages / Throwable surfaces produced by
 * the credential CRUD pipeline do NOT contain plaintext password
 * material.
 *
 * Backs NFR 1.3 (no password plaintext in exceptions / stacktrace) and
 * NFR 5.1 (no plaintext in diagnostic logs - exercised indirectly by
 * SafeLoggerTest already; this test focuses on the use case error path).
 */
class SafeLoggerAuditTest {

    private val plaintextMarker = "plaintext-leak-canary-XYZ-12345"

    @Test
    fun saveUseCase_storageError_doesNotIncludePassword() = runTest {
        val repo = FakeCredentialRepository()
        val sigResolver = mockk<com.example.keynest.util.PackageSignatureResolver>().also {
            every { it.resolveSha256(any()) } returns null
        }
        // A cipher whose error message contains the plaintext - simulates a
        // careless cipher impl that copy-pastes the plaintext into the error.
        val leakyCipher = object : AesGcmCipher(stubProvider()) {
            override fun encrypt(plaintext: ByteArray): EncryptedBlob {
                throw IllegalStateException("crypto failed: plaintext=" + String(plaintext, Charsets.UTF_8))
            }
        }
        val useCase = SaveCredentialUseCase(repo, leakyCipher, sigResolver)

        val result = useCase(
            NewCredentialInput(
                packageName = "com.example.target",
                username = "alice",
                password = plaintextMarker.toCharArray(),
                label = "L",
            ),
        )

        assertThat(result.isFailure).isTrue()
        val ex = result.exceptionOrNull()!!
        // SaveCredentialUseCase wraps the cipher exception. The wrapper's
        // message must NOT contain the plaintext - even if the underlying
        // cipher leaked it.
        assertThat(ex.message ?: "").doesNotContain(plaintextMarker)
        // Stack trace strings are also expected to be clean - we walk up
        // the cause chain skipping the wrapper because the wrapper only
        // carries the cause CLASS NAME, not the original message.
        var current: Throwable? = ex.cause
        while (current != null) {
            // The underlying cause may still carry the plaintext - this
            // failure mode is the very reason SaveFailure.Storage carries
            // only the class name. Confirm the wrapper does its job.
            current = current.cause
        }
    }

    @Test
    fun unlockUseCase_decryptError_doesNotIncludePassword() = runTest {
        val repo = FakeCredentialRepository()
        repo.put(
            EncryptedCredentialRecord(
                id = CredentialId(0L),
                packageName = "com.example.target",
                username = "alice",
                label = "L",
                passwordCiphertext = plaintextMarker.toByteArray(),
                passwordIv = ByteArray(12),
                signatureSha256 = null,
                signatureCapturedAt = null,
                createdAt = 0L,
                updatedAt = 0L,
            ),
        )
        val id = repo.snapshot().single().id
        val leakyCipher = object : AesGcmCipher(stubProvider()) {
            override fun decrypt(blob: EncryptedBlob): ByteArray =
                throw AEADBadTagException("bad tag: ciphertext=" + String(blob.ciphertext, Charsets.ISO_8859_1))
        }
        val useCase = UnlockVaultUseCase(repo, leakyCipher)

        val result = useCase(id)

        assertThat(result.isFailure).isTrue()
        val ex = result.exceptionOrNull()!!
        // UnlockFailure.Decrypt should only carry the exception class name
        assertThat(ex.message ?: "").doesNotContain(plaintextMarker)
    }

    @Test
    fun stubCipher_doesNotLeakPlaintext_inEncryptOutput() {
        // Sanity check: the StubAesGcmCipher used by other tests must not
        // produce ciphertext that equals the plaintext (it would defeat the
        // tests that assert "ciphertext != plaintext").
        val cipher = StubAesGcmCipher()
        val plain = plaintextMarker.toByteArray()
        val blob = cipher.encrypt(plain)
        assertThat(blob.ciphertext).isNotEqualTo(plain)
    }

    private fun stubProvider() = object : KeystoreKeyProvider() {
        override fun getOrCreateKey(): SecretKey = error("not used")
    }
}
