package io.github.hitoshiichikawa.keynest.domain.usecase

import com.google.common.truth.Truth.assertThat
import io.github.hitoshiichikawa.keynest.domain.model.CustomField
import io.github.hitoshiichikawa.keynest.security.EncryptedCustomFieldsCodec
import io.github.hitoshiichikawa.keynest.util.PackageSignatureResolver
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Test

/**
 * Behaviour of [SaveCredentialUseCase] and [UpdateCredentialUseCase] with
 * respect to the new [CustomField] support. Issue #66 Phase 1.
 *
 * Backs Req 1.4 (AES-GCM encryption of customField values), Req 6.6
 * (round-trip), and the design.md §6.1 "empty list still produces a
 * non-empty ciphertext" invariant.
 */
class SaveCredentialUseCaseCustomFieldsTest {

    private val repo = FakeCredentialRepository()
    private val cipher = StubAesGcmCipher()
    private val codec = EncryptedCustomFieldsCodec(cipher)
    private val sigResolver = mockk<PackageSignatureResolver>().also {
        every { it.resolveSha256(any()) } returns null
    }

    @Test
    fun save_persistsEncryptedCustomFields_andRoundTripsViaCodec() = runTest {
        val useCase = SaveCredentialUseCase(repo, cipher, sigResolver, codec) { 1L }
        val fields = listOf(
            CustomField("memberId", "M-9999"),
            CustomField("storeCode", "TKO-001"),
        )

        val result = useCase(
            NewCredentialInput(
                packageName = "com.example.target",
                username = "alice",
                password = "pw".toCharArray(),
                label = "Example",
                customFields = fields,
            ),
        )

        assertThat(result.isSuccess).isTrue()
        val record = repo.snapshot().single()
        // ciphertext + iv are non-empty even though the plaintext is short.
        assertThat(record.customFieldsCiphertext).isNotEmpty()
        assertThat(record.customFieldsIv).hasLength(12)
        // Round-trip through the codec returns the original list.
        val decrypted = codec.decrypt(
            io.github.hitoshiichikawa.keynest.security.EncryptedBlob(
                iv = record.customFieldsIv,
                ciphertext = record.customFieldsCiphertext,
            ),
        )
        assertThat(decrypted).containsExactlyElementsIn(fields).inOrder()
    }

    @Test
    fun save_withDefaultEmptyCustomFields_stillPersistsNonEmptyBlob() = runTest {
        // The default value on NewCredentialInput is empty. The codec
        // serialises `[]` so the persisted ciphertext is still non-empty
        // (uniform persistence shape — design.md §6.1).
        val useCase = SaveCredentialUseCase(repo, cipher, sigResolver, codec) { 1L }

        useCase(
            NewCredentialInput(
                packageName = "com.example.target",
                username = "alice",
                password = "pw".toCharArray(),
                label = "Example",
                // customFields omitted -> default emptyList()
            ),
        )

        val record = repo.snapshot().single()
        assertThat(record.customFieldsCiphertext).isNotEmpty()
        assertThat(record.customFieldsIv).hasLength(12)
        val decrypted = codec.decrypt(
            io.github.hitoshiichikawa.keynest.security.EncryptedBlob(
                iv = record.customFieldsIv,
                ciphertext = record.customFieldsCiphertext,
            ),
        )
        assertThat(decrypted).isEmpty()
    }

    @Test
    fun update_withNullCustomFields_preservesExistingCiphertext() = runTest {
        // Seed a record with a known customFields ciphertext.
        val seedBlob = codec.encrypt(listOf(CustomField("k", "v")))
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
                customFieldsCiphertext = seedBlob.ciphertext,
                customFieldsIv = seedBlob.iv,
            ),
        )
        val id = repo.snapshot().single().id
        val useCase = UpdateCredentialUseCase(repo, cipher, sigResolver, codec) { 999L }

        useCase(
            UpdateCredentialInput(
                id = id,
                packageName = "com.example.target",
                username = "alice2",
                label = "L2",
                newPassword = null,
                customFields = null, // <- preserve existing
            ),
        )

        val updated = repo.snapshot().single()
        assertThat(updated.customFieldsCiphertext).isEqualTo(seedBlob.ciphertext)
        assertThat(updated.customFieldsIv).isEqualTo(seedBlob.iv)
        assertThat(updated.username).isEqualTo("alice2")
    }

    @Test
    fun update_withNonNullCustomFields_replacesCiphertext() = runTest {
        val seedBlob = codec.encrypt(listOf(CustomField("old", "old-val")))
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
                customFieldsCiphertext = seedBlob.ciphertext,
                customFieldsIv = seedBlob.iv,
            ),
        )
        val id = repo.snapshot().single().id
        val useCase = UpdateCredentialUseCase(repo, cipher, sigResolver, codec) { 999L }
        val newFields = listOf(CustomField("brandNew", "fresh"))

        useCase(
            UpdateCredentialInput(
                id = id,
                packageName = "com.example.target",
                username = "alice",
                label = "L",
                newPassword = null,
                customFields = newFields,
            ),
        )

        val updated = repo.snapshot().single()
        val roundTripped = codec.decrypt(
            io.github.hitoshiichikawa.keynest.security.EncryptedBlob(
                iv = updated.customFieldsIv,
                ciphertext = updated.customFieldsCiphertext,
            ),
        )
        assertThat(roundTripped).containsExactlyElementsIn(newFields).inOrder()
    }

}
