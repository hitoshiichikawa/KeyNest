package inc.goodanswers.keynest.domain.usecase

import inc.goodanswers.keynest.domain.model.CredentialId
import inc.goodanswers.keynest.domain.model.EncryptedCredentialRecord
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Test

/**
 * Behaviour of [ObserveRecentlyUsedUseCase]. Issue #9 Req 3.1, 3.3, 3.4,
 * 3.6, 3.7.
 */
class ObserveRecentlyUsedUseCaseTest {

    private val repo = FakeCredentialRepository()
    private val useCase = ObserveRecentlyUsedUseCase(repo)

    @Test
    fun invoke_returnsTopFiveByLastUsedAtDescending() = runTest {
        // Arrange: 7 credentials, all used.
        val ids = (1..7L).map { idx ->
            repo.save(blankRecord("u$idx"))
        }
        // Stamp each with a distinct lastUsedAt so ordering is unambiguous.
        ids.forEachIndexed { index, id ->
            repo.markUsed(id, timestamp = index * 10L) // 0, 10, 20, ... 60
        }

        // Act
        val emitted = useCase().first()

        // Assert: 5 newest (timestamps 60, 50, 40, 30, 20) in descending
        // order, which maps back to users u7, u6, u5, u4, u3.
        assertThat(emitted).hasSize(5)
        assertThat(emitted.map { it.username })
            .containsExactly("u7", "u6", "u5", "u4", "u3")
            .inOrder()
    }

    @Test
    fun invoke_returnsFewerThanFive_whenOnlyFewRowsAreUsed() = runTest {
        // Req 3.3: do not pad with placeholders.
        val id1 = repo.save(blankRecord("u1"))
        repo.save(blankRecord("u2")) // never used
        repo.markUsed(id1, timestamp = 1L)

        val emitted = useCase().first()

        assertThat(emitted).hasSize(1)
        assertThat(emitted.single().username).isEqualTo("u1")
    }

    @Test
    fun invoke_returnsEmptyList_whenNoCredentialHasBeenUsed() = runTest {
        // Req 3.4.
        repo.save(blankRecord("u1"))
        repo.save(blankRecord("u2"))

        val emitted = useCase().first()

        assertThat(emitted).isEmpty()
    }

    @Test
    fun invoke_returnsEmptyList_whenRepositoryIsEmpty() = runTest {
        // Boundary case: no credentials at all.
        val emitted = useCase().first()
        assertThat(emitted).isEmpty()
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
