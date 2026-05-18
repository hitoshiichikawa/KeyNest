package io.github.hitoshiichikawa.keynest.domain.usecase

import com.google.common.truth.Truth.assertThat
import io.github.hitoshiichikawa.keynest.domain.model.CredentialId
import io.github.hitoshiichikawa.keynest.domain.model.CustomField
import io.github.hitoshiichikawa.keynest.domain.model.EncryptedCredentialRecord
import io.github.hitoshiichikawa.keynest.security.EncryptedCustomFieldsCodec
import kotlinx.coroutines.test.runTest
import org.junit.Test

/**
 * customFields behaviour of [UnlockVaultUseCase]. Issue #66 Phase 1.
 *
 * Backs Req 6.6 (round-trip) and design.md §5.4 / §6.1 fallbacks.
 */
class UnlockVaultUseCaseCustomFieldsTest {

    private val repo = FakeCredentialRepository()
    private val cipher = StubAesGcmCipher()
    private val codec = EncryptedCustomFieldsCodec(cipher)

    private fun seedWithCustomFields(fields: List<CustomField>): CredentialId {
        val passwordBlob = cipher.encrypt("p@ssw0rd".toByteArray(Charsets.UTF_8))
        val customFieldsBlob = codec.encrypt(fields)
        repo.put(
            EncryptedCredentialRecord(
                id = CredentialId(0L),
                packageName = "com.example.target",
                username = "alice",
                label = "Example",
                passwordCiphertext = passwordBlob.ciphertext,
                passwordIv = passwordBlob.iv,
                signatureSha256 = null,
                signatureCapturedAt = null,
                createdAt = 0L,
                updatedAt = 0L,
                customFieldsCiphertext = customFieldsBlob.ciphertext,
                customFieldsIv = customFieldsBlob.iv,
            ),
        )
        return repo.snapshot().single().id
    }

    @Test
    fun invoke_returnsDecryptedCustomFields_onUnlock() = runTest {
        val expected = listOf(
            CustomField("memberId", "M-9999"),
            CustomField("storeCode", "TKO-001"),
        )
        val id = seedWithCustomFields(expected)
        val useCase = UnlockVaultUseCase(repo, cipher, codec)

        val result = useCase(id)

        assertThat(result.isSuccess).isTrue()
        result.getOrNull()!!.use { plain ->
            assertThat(plain.customFields).containsExactlyElementsIn(expected).inOrder()
            // Existing username / password path unchanged.
            assertThat(plain.username).isEqualTo("alice")
            assertThat(String(plain.password)).isEqualTo("p@ssw0rd")
        }
    }

    @Test
    fun invoke_returnsEmptyCustomFields_whenCiphertextIsEmpty_migrationDefault() = runTest {
        // Simulate a migrated v2 row: customFields columns are zero-length
        // BLOBs. The codec's empty-BLOB fast path returns an empty list.
        val passwordBlob = cipher.encrypt("p@ssw0rd".toByteArray(Charsets.UTF_8))
        repo.put(
            EncryptedCredentialRecord(
                id = CredentialId(0L),
                packageName = "com.example.target",
                username = "alice",
                label = "Example",
                passwordCiphertext = passwordBlob.ciphertext,
                passwordIv = passwordBlob.iv,
                signatureSha256 = null,
                signatureCapturedAt = null,
                createdAt = 0L,
                updatedAt = 0L,
                customFieldsCiphertext = ByteArray(0),
                customFieldsIv = ByteArray(0),
            ),
        )
        val id = repo.snapshot().single().id
        val useCase = UnlockVaultUseCase(repo, cipher, codec)

        val result = useCase(id)

        assertThat(result.isSuccess).isTrue()
        result.getOrNull()!!.use { plain ->
            assertThat(plain.customFields).isEmpty()
            assertThat(String(plain.password)).isEqualTo("p@ssw0rd")
        }
    }

    @Test
    fun close_emptiesCustomFieldsList() = runTest {
        // After close() the PlaintextCredential no longer exposes the
        // decrypted customFields (best-effort cleanup — String contents
        // cannot be wiped deterministically on the JVM).
        val id = seedWithCustomFields(listOf(CustomField("k", "v")))
        val useCase = UnlockVaultUseCase(repo, cipher, codec)

        val plain = useCase(id).getOrThrow()
        assertThat(plain.customFields).hasSize(1)
        plain.close()
        assertThat(plain.customFields).isEmpty()
    }
}
