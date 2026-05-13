package com.example.keynest.domain.usecase

import com.example.keynest.domain.model.CredentialId
import com.example.keynest.domain.model.EncryptedCredentialRecord
import com.example.keynest.domain.model.SigningHash
import com.example.keynest.domain.model.VaultMetadata
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Test

/**
 * Behaviour of [ObserveVaultMetadataUseCase]. Issue #10 Req 4.1, 4.2,
 * 4.3, 4.5, 4.6.
 */
class ObserveVaultMetadataUseCaseTest {

    private val repo = FakeCredentialRepository()
    private val useCase = ObserveVaultMetadataUseCase(repo)

    @Test
    fun invoke_emitsZeroCountAndNullTimestamp_onEmptyVault() = runTest {
        // Req 4.3: empty vault must NOT surface a synthetic / zero
        // timestamp -- the UI relies on null to switch to the "未登録"
        // placeholder.
        val emitted = useCase().first()

        assertThat(emitted).isEqualTo(VaultMetadata(count = 0, latestUpdatedAt = null))
    }

    @Test
    fun invoke_returnsCountAndMaxUpdatedAt_acrossRows() = runTest {
        // Req 4.1, 4.2.
        repo.put(sample(label = "a", updatedAt = 100L))
        repo.put(sample(label = "b", updatedAt = 500L))
        repo.put(sample(label = "c", updatedAt = 300L))

        val emitted = useCase().first()

        assertThat(emitted.count).isEqualTo(3)
        assertThat(emitted.latestUpdatedAt).isEqualTo(500L)
    }

    @Test
    fun invoke_emitsZeroAndNull_afterClearAll() = runTest {
        // Req 4.3 + 7.5: clearAll resets both count and latestUpdatedAt.
        repo.put(sample(label = "x", updatedAt = 100L))
        repo.clearAll()

        val emitted = useCase().first()

        assertThat(emitted.count).isEqualTo(0)
        assertThat(emitted.latestUpdatedAt).isNull()
    }

    private fun sample(label: String, updatedAt: Long) = EncryptedCredentialRecord(
        id = CredentialId(0L),
        packageName = "com.example.$label",
        username = "user-$label",
        label = label,
        passwordCiphertext = byteArrayOf(0x01, 0x02, 0x03),
        passwordIv = ByteArray(12) { 0x10.toByte() },
        signatureSha256 = SigningHash(ByteArray(32) { 0x20.toByte() }),
        signatureCapturedAt = 1000L,
        createdAt = updatedAt,
        updatedAt = updatedAt,
    )
}
