package inc.goodanswers.keynest.domain.usecase

import inc.goodanswers.keynest.domain.model.CredentialId
import inc.goodanswers.keynest.domain.model.DuplicateFailure
import inc.goodanswers.keynest.domain.model.EncryptedCredentialRecord
import inc.goodanswers.keynest.domain.model.SigningHash
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.test.runTest
import org.junit.Test

/**
 * Behaviour of [DuplicateCredentialUseCase]. Issue #9 Req 5.3, 5.4, NFR
 * 1.3, NFR 1.4.
 */
class DuplicateCredentialUseCaseTest {

    private val repo = FakeCredentialRepository()
    private val useCase = DuplicateCredentialUseCase(repo, now = { TIMESTAMP })

    @Test
    fun invoke_inheritsAllFields_andResetsTimestamps() = runTest {
        // Arrange: a fully populated source row.
        val sourceId = repo.save(
            sample("alice").copy(
                createdAt = 100L,
                updatedAt = 200L,
                lastUsedAt = 300L,
                signatureSha256 = SigningHash(ByteArray(32) { 0x33.toByte() }),
                signatureCapturedAt = 400L,
            ),
        )
        val source = repo.findById(sourceId)!!

        // Act
        val result = useCase(sourceId)

        // Assert
        assertThat(result.isSuccess).isTrue()
        val newId = result.getOrThrow()
        assertThat(newId.value).isNotEqualTo(sourceId.value)
        val copy = repo.findById(newId)!!
        // Inherited
        assertThat(copy.label).isEqualTo(source.label)
        assertThat(copy.username).isEqualTo(source.username)
        assertThat(copy.packageName).isEqualTo(source.packageName)
        assertThat(copy.passwordCiphertext).isEqualTo(source.passwordCiphertext)
        assertThat(copy.passwordIv).isEqualTo(source.passwordIv)
        assertThat(copy.signatureSha256).isEqualTo(source.signatureSha256)
        assertThat(copy.signatureCapturedAt).isEqualTo(source.signatureCapturedAt)
        // Reset
        assertThat(copy.createdAt).isEqualTo(TIMESTAMP)
        assertThat(copy.updatedAt).isEqualTo(TIMESTAMP)
        assertThat(copy.lastUsedAt).isNull() // duplicate is "never used"
    }

    @Test
    fun invoke_leavesSourceUnchanged() = runTest {
        // Req 5.4: source must be byte-for-byte identical post-duplicate.
        val sourceId = repo.save(sample("alice").copy(createdAt = 100L, updatedAt = 200L))
        val before = repo.findById(sourceId)!!

        useCase(sourceId)

        val after = repo.findById(sourceId)!!
        assertThat(after).isEqualTo(before)
    }

    @Test
    fun invoke_addsRow_soListGrowsByOne() = runTest {
        // Req 5.4: duplicate participates in the list / carousel.
        val sourceId = repo.save(sample("alice"))
        val before = repo.snapshot().size

        useCase(sourceId)

        assertThat(repo.snapshot().size).isEqualTo(before + 1)
    }

    @Test
    fun invoke_returnsNotFound_whenSourceIdIsUnknown() = runTest {
        // Req 5.3 failure path.
        val result = useCase(CredentialId(9999L))

        assertThat(result.isFailure).isTrue()
        assertThat(result.exceptionOrNull()).isInstanceOf(DuplicateFailure.NotFound::class.java)
    }

    private fun sample(username: String) = EncryptedCredentialRecord(
        id = CredentialId(0L),
        packageName = "com.example.$username",
        username = username,
        label = "Label-$username",
        passwordCiphertext = byteArrayOf(0x01, 0x02, 0x03),
        passwordIv = ByteArray(12) { 0x10.toByte() },
        signatureSha256 = SigningHash(ByteArray(32) { 0x22.toByte() }),
        signatureCapturedAt = 500L,
        createdAt = 0L,
        updatedAt = 0L,
    )

    private companion object {
        const val TIMESTAMP = 7777L
    }
}
