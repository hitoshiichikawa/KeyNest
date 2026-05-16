package inc.goodanswers.keynest.domain.usecase

import inc.goodanswers.keynest.domain.model.SigningHash
import inc.goodanswers.keynest.util.PackageSignatureResolver
import com.google.common.truth.Truth.assertThat
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Test
import java.security.MessageDigest

/**
 * Behaviour of [SaveCredentialUseCase].
 *
 * Covers Req 1.1, 1.2, 1.3, 1.4, 2.1, 2.2 and NFR 1.3 (no plaintext in
 * exceptions). The cipher is a non-Keystore stub so the tests run as plain
 * JVM unit tests.
 */
class SaveCredentialUseCaseTest {

    private val repo = FakeCredentialRepository()
    private val cipher = StubAesGcmCipher()
    private val sigResolver = mockk<PackageSignatureResolver>()

    // ---- Req 1.1 / 1.2 / 2.1 ---------------------------------------------

    @Test
    fun invoke_savesRecord_encryptsPassword_andCapturesSignature() = runTest {
        val pkg = "com.example.target"
        val signerHash = SigningHash(MessageDigest.getInstance("SHA-256").digest("CERT".toByteArray()))
        every { sigResolver.resolveSha256(pkg) } returns signerHash
        val useCase = SaveCredentialUseCase(repo, cipher, sigResolver) { 42L }
        val password = "p@ssw0rd-secret".toCharArray()

        val result = useCase(
            NewCredentialInput(
                packageName = pkg,
                username = "alice",
                password = password,
                label = "Example",
            ),
        )

        assertThat(result.isSuccess).isTrue()
        val records = repo.snapshot()
        assertThat(records).hasSize(1)
        val rec = records[0]
        // Req 1.2 / NFR 1.1: plaintext must NOT appear anywhere in the persisted record's bytes.
        assertThat(String(rec.passwordCiphertext, Charsets.UTF_8)).doesNotContain("p@ssw0rd")
        assertThat(rec.passwordCiphertext).isNotEqualTo("p@ssw0rd-secret".toByteArray())
        // Req 2.1: signing hash captured.
        assertThat(rec.signatureSha256).isEqualTo(signerHash)
        assertThat(rec.signatureCapturedAt).isEqualTo(42L)
        // Timestamps applied.
        assertThat(rec.createdAt).isEqualTo(42L)
        assertThat(rec.updatedAt).isEqualTo(42L)
        // password CharArray is wiped (Req 5.5 / NFR 1.3)
        assertThat(password).isEqualTo(CharArray(password.size) { ' ' })
    }

    // ---- Req 2.2 ---------------------------------------------------------

    @Test
    fun invoke_savesRecord_withNullSignature_whenAppNotInstalled() = runTest {
        every { sigResolver.resolveSha256("com.example.target") } returns null
        val useCase = SaveCredentialUseCase(repo, cipher, sigResolver) { 100L }

        val result = useCase(
            NewCredentialInput(
                packageName = "com.example.target",
                username = "alice",
                password = "pw".toCharArray(),
                label = "Example",
            ),
        )

        assertThat(result.isSuccess).isTrue()
        val rec = repo.snapshot().single()
        assertThat(rec.signatureSha256).isNull()
        assertThat(rec.signatureCapturedAt).isNull()
    }

    // ---- Req 1.3 ---------------------------------------------------------

    @Test
    fun invoke_failsValidation_whenPackageNameIsBlank() = runTest {
        val useCase = SaveCredentialUseCase(repo, cipher, sigResolver)
        val pw = "pw".toCharArray()

        val result = useCase(
            NewCredentialInput(
                packageName = "  ",
                username = "alice",
                password = pw,
                label = "Example",
            ),
        )

        assertThat(result.isFailure).isTrue()
        assertThat(result.exceptionOrNull()).isInstanceOf(SaveFailure.PackageNameBlank::class.java)
        assertThat(repo.snapshot()).isEmpty()
        // password CharArray still wiped on the failure path.
        assertThat(pw).isEqualTo(CharArray(pw.size) { ' ' })
    }

    @Test
    fun invoke_failsValidation_whenPackageNameIsMalformed() = runTest {
        val useCase = SaveCredentialUseCase(repo, cipher, sigResolver)

        val resultNoDot = useCase(NewCredentialInput("singletoken", "alice", "pw".toCharArray(), "L"))
        val resultLeadDot = useCase(NewCredentialInput(".com.example", "alice", "pw".toCharArray(), "L"))
        val resultTrailDot = useCase(NewCredentialInput("com.example.", "alice", "pw".toCharArray(), "L"))
        val resultStartsDigit = useCase(NewCredentialInput("9example.foo", "alice", "pw".toCharArray(), "L"))

        listOf(resultNoDot, resultLeadDot, resultTrailDot, resultStartsDigit).forEach {
            assertThat(it.isFailure).isTrue()
            assertThat(it.exceptionOrNull()).isInstanceOf(SaveFailure.PackageNameInvalid::class.java)
        }
        assertThat(repo.snapshot()).isEmpty()
    }

    @Test
    fun invoke_failsValidation_whenUsernameIsBlank() = runTest {
        val useCase = SaveCredentialUseCase(repo, cipher, sigResolver)
        val r = useCase(NewCredentialInput("com.example.target", "  ", "pw".toCharArray(), "L"))
        assertThat(r.exceptionOrNull()).isInstanceOf(SaveFailure.UsernameBlank::class.java)
    }

    @Test
    fun invoke_failsValidation_whenPasswordIsEmpty() = runTest {
        val useCase = SaveCredentialUseCase(repo, cipher, sigResolver)
        val r = useCase(NewCredentialInput("com.example.target", "alice", charArrayOf(), "L"))
        assertThat(r.exceptionOrNull()).isInstanceOf(SaveFailure.PasswordBlank::class.java)
    }

    @Test
    fun invoke_failsValidation_whenLabelIsBlank() = runTest {
        val useCase = SaveCredentialUseCase(repo, cipher, sigResolver)
        val r = useCase(NewCredentialInput("com.example.target", "alice", "pw".toCharArray(), "  "))
        assertThat(r.exceptionOrNull()).isInstanceOf(SaveFailure.LabelBlank::class.java)
    }

    // ---- Req 1.4 ---------------------------------------------------------

    @Test
    fun invoke_allowsMultipleCredentialsForSamePackage() = runTest {
        every { sigResolver.resolveSha256("com.example.target") } returns null
        val useCase = SaveCredentialUseCase(repo, cipher, sigResolver) { 1L }

        useCase(NewCredentialInput("com.example.target", "alice", "p1".toCharArray(), "L1"))
        useCase(NewCredentialInput("com.example.target", "bob", "p2".toCharArray(), "L2"))

        assertThat(repo.snapshot()).hasSize(2)
        assertThat(repo.snapshot().map { it.username }).containsExactly("alice", "bob")
    }

    // ---- NFR 1.3 ---------------------------------------------------------

    @Test
    fun storageFailure_doesNotIncludePlaintextInException() = runTest {
        // Arrange: signature resolver works, but the underlying cipher throws
        // - simulate by injecting a cipher that always blows up.
        every { sigResolver.resolveSha256(any()) } returns null
        val throwingCipher = object : inc.goodanswers.keynest.security.AesGcmCipher(stubProvider()) {
            override fun encrypt(plaintext: ByteArray): inc.goodanswers.keynest.security.EncryptedBlob =
                throw IllegalStateException("plaintext=" + String(plaintext, Charsets.UTF_8))
        }
        val useCase = SaveCredentialUseCase(repo, throwingCipher, sigResolver) { 1L }
        val secret = "leaky-secret".toCharArray()

        val result = useCase(NewCredentialInput("com.example.target", "alice", secret, "L"))

        assertThat(result.isFailure).isTrue()
        val ex = result.exceptionOrNull()!!
        // The wrapper exception forwarded to callers must not contain the plaintext.
        assertThat(ex.message ?: "").doesNotContain("leaky-secret")
        assertThat(ex).isInstanceOf(SaveFailure.Storage::class.java)
    }

    private fun stubProvider() = object : inc.goodanswers.keynest.security.KeystoreKeyProvider() {
        override fun getOrCreateKey(): javax.crypto.SecretKey = error("should not be called")
    }
}
