package io.github.hitoshiichikawa.keynest.credentialprovider.registration

import com.google.common.truth.Truth.assertThat
import io.github.hitoshiichikawa.keynest.domain.model.Passkey
import io.github.hitoshiichikawa.keynest.domain.repository.PasskeyRepository
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Test

/**
 * Issue #136: [ExcludeCredentialDetector.containsAny] の rpId スコープ付き
 * 照合の検証。WebAuthn §6.3.2 step 5 — 除外対象は「同一 RP に bound な
 * クレデンシャル」のみで、別 RP の credentialId と一致しても除外しない。
 */
class ExcludeCredentialDetectorTest {

    private val repository = mockk<PasskeyRepository>()
    private val detector = ExcludeCredentialDetector(repository)

    @Test
    fun containsAny_emptyIds_returnsFalseWithoutQueryingRepository() = runTest {
        // Arrange: (coEvery 無し — 呼ばれたら mockk が即座に失敗する)

        // Act
        val result = detector.containsAny(rpId = "example.com", credentialIds = emptyList())

        // Assert
        assertThat(result).isFalse()
        coVerify(exactly = 0) { repository.findByCredentialId(any()) }
    }

    @Test
    fun containsAny_idExistsForSameRpId_returnsTrue() = runTest {
        // Arrange
        coEvery { repository.findByCredentialId("known-id") } returns
            samplePasskey("known-id", rpId = "example.com")

        // Act
        val result = detector.containsAny(rpId = "example.com", credentialIds = listOf("known-id"))

        // Assert
        assertThat(result).isTrue()
    }

    @Test
    fun containsAny_idExistsButForDifferentRpId_returnsFalse() = runTest {
        // Arrange: 別 RP の row と credentialId が偶然一致しても除外理由に
        // ならない（WebAuthn §6.3.2 step 5 の rpId 一致条件）。
        coEvery { repository.findByCredentialId("cross-rp-id") } returns
            samplePasskey("cross-rp-id", rpId = "other.example")

        // Act
        val result = detector.containsAny(rpId = "example.com", credentialIds = listOf("cross-rp-id"))

        // Assert
        assertThat(result).isFalse()
    }

    @Test
    fun containsAny_idAbsentFromVault_returnsFalse() = runTest {
        // Arrange
        coEvery { repository.findByCredentialId("unknown-id") } returns null

        // Act
        val result = detector.containsAny(rpId = "example.com", credentialIds = listOf("unknown-id"))

        // Assert
        assertThat(result).isFalse()
    }

    @Test
    fun containsAny_mixedList_returnsTrueWhenAnyMatchesSameRp() = runTest {
        // Arrange: 欠落 2 件 + 同一 RP 一致 1 件の混在
        coEvery { repository.findByCredentialId("missing-1") } returns null
        coEvery { repository.findByCredentialId("missing-2") } returns null
        coEvery { repository.findByCredentialId("hit") } returns
            samplePasskey("hit", rpId = "example.com")

        // Act
        val result = detector.containsAny(
            rpId = "example.com",
            credentialIds = listOf("missing-1", "missing-2", "hit"),
        )

        // Assert
        assertThat(result).isTrue()
    }

    /** [GetEntryBuilderTest] の sampleEntity と同型の最小 fixture。 */
    private fun samplePasskey(
        credentialId: String,
        rpId: String,
    ): Passkey = Passkey(
        credentialId = credentialId,
        rpId = rpId,
        rpDisplayName = "Example",
        userHandle = ByteArray(16) { 0x77 },
        userName = "alice@example.com",
        userDisplayName = "Alice",
        isDiscoverable = true,
        signCount = 0L,
        displayName = null,
        createdAt = 1_700_000_000_000L,
        lastUsedAt = null,
    )
}
