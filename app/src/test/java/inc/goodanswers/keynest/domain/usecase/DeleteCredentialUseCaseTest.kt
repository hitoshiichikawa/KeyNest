package inc.goodanswers.keynest.domain.usecase

import inc.goodanswers.keynest.domain.model.CredentialId
import inc.goodanswers.keynest.domain.model.EncryptedCredentialRecord
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.test.runTest
import org.junit.Test

/** Behaviour of [DeleteCredentialUseCase]. Covers Req 1.5. */
class DeleteCredentialUseCaseTest {

    private val repo = FakeCredentialRepository()
    private val useCase = DeleteCredentialUseCase(repo)

    private fun seed(): CredentialId {
        repo.put(
            EncryptedCredentialRecord(
                id = CredentialId(0L),
                packageName = "com.example.target",
                username = "alice",
                label = "L",
                passwordCiphertext = byteArrayOf(1),
                passwordIv = ByteArray(12),
                signatureSha256 = null,
                signatureCapturedAt = null,
                createdAt = 0L,
                updatedAt = 0L,
            ),
        )
        return repo.snapshot().single().id
    }

    @Test
    fun delete_removesCredential() = runTest {
        val id = seed()
        val result = useCase(id)

        assertThat(result.isSuccess).isTrue()
        assertThat(repo.snapshot()).isEmpty()
    }

    @Test
    fun delete_isNoOp_forUnknownId() = runTest {
        seed()
        val result = useCase(CredentialId(9999L))
        assertThat(result.isSuccess).isTrue()
        assertThat(repo.snapshot()).hasSize(1)
    }
}
