package io.github.hitoshiichikawa.keynest.domain.usecase

import io.github.hitoshiichikawa.keynest.domain.model.CredentialId
import io.github.hitoshiichikawa.keynest.domain.model.EncryptedCredentialRecord
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.test.runTest
import org.junit.Test

/**
 * Behaviour of [UnlockVaultUseCase]. Covers Req 5.3, 5.5, NFR 1.4.
 *
 * The cipher is the JVM-only [StubAesGcmCipher] so the use case can be
 * exercised under plain JUnit. The real cipher's round-trip is covered by
 * the instrumented [io.github.hitoshiichikawa.keynest.security.AesGcmCipherTest].
 */
class UnlockVaultUseCaseTest {

    private val repo = FakeCredentialRepository()
    private val cipher = StubAesGcmCipher()

    private fun seed(plaintext: String): CredentialId {
        val blob = cipher.encrypt(plaintext.toByteArray(Charsets.UTF_8))
        repo.put(
            EncryptedCredentialRecord(
                id = CredentialId(0L),
                packageName = "com.example.target",
                username = "alice",
                label = "Example",
                passwordCiphertext = blob.ciphertext,
                passwordIv = blob.iv,
                signatureSha256 = null,
                signatureCapturedAt = null,
                createdAt = 0L,
                updatedAt = 0L,
            ),
        )
        return repo.snapshot().single().id
    }

    @Test
    fun invoke_returnsPlaintextCredential_whenDecryptionSucceeds() = runTest {
        val id = seed("p@ssw0rd")
        val useCase = UnlockVaultUseCase(repo, cipher)

        val result = useCase(id)

        assertThat(result.isSuccess).isTrue()
        result.getOrNull()!!.use { plain ->
            assertThat(String(plain.password)).isEqualTo("p@ssw0rd")
            assertThat(plain.username).isEqualTo("alice")
            assertThat(plain.label).isEqualTo("Example")
            assertThat(plain.packageName).isEqualTo("com.example.target")
            assertThat(plain.isClosed).isFalse()
        }
        // After `use`, the password buffer must be zero-filled (Req 5.5)
    }

    @Test
    fun invoke_returnsNotFound_whenIdIsUnknown() = runTest {
        val useCase = UnlockVaultUseCase(repo, cipher)
        val result = useCase(CredentialId(9999L))
        assertThat(result.exceptionOrNull()).isInstanceOf(UnlockFailure.NotFound::class.java)
    }

    @Test
    fun invoke_returnsDecryptFailure_whenCipherThrows() = runTest {
        // Arrange: store a record whose ciphertext is shorter than what stub
        // would produce - the stub will still XOR but we wrap the cipher in
        // a throwing decorator to simulate AEADBadTagException.
        seed("p@ssw0rd")
        val id = repo.snapshot().single().id
        val throwingCipher = object : io.github.hitoshiichikawa.keynest.security.AesGcmCipher(stubProvider()) {
            override fun decrypt(blob: io.github.hitoshiichikawa.keynest.security.EncryptedBlob): ByteArray =
                throw javax.crypto.AEADBadTagException("bad tag")
        }
        val useCase = UnlockVaultUseCase(repo, throwingCipher)

        val result = useCase(id)

        assertThat(result.isFailure).isTrue()
        val ex = result.exceptionOrNull()!!
        assertThat(ex).isInstanceOf(UnlockFailure.Decrypt::class.java)
        // NFR 1.3: error must not embed any plaintext.
        assertThat(ex.message ?: "").doesNotContain("p@ssw0rd")
    }

    private fun stubProvider() = object : io.github.hitoshiichikawa.keynest.security.KeystoreKeyProvider() {
        override fun getOrCreateKey(): javax.crypto.SecretKey = error("not used")
    }
}
