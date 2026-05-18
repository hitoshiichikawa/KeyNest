package io.github.hitoshiichikawa.keynest.domain.usecase

import io.github.hitoshiichikawa.keynest.data.entity.DetectedFieldSource
import io.github.hitoshiichikawa.keynest.domain.model.ClearVaultFailure
import io.github.hitoshiichikawa.keynest.domain.model.CredentialId
import io.github.hitoshiichikawa.keynest.domain.model.DetectedField
import io.github.hitoshiichikawa.keynest.domain.model.EncryptedCredentialRecord
import io.github.hitoshiichikawa.keynest.domain.model.SigningHash
import io.github.hitoshiichikawa.keynest.security.KeystoreKeyProvider
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Test

/**
 * Behaviour of [ClearVaultUseCase]. Issue #10 Req 7.5, 7.7, 7.8, NFR
 * 1.3, NFR 1.4.
 */
class ClearVaultUseCaseTest {

    @Test
    fun invoke_clearsRepository_andDeletesKeystoreAlias_onSuccess() = runTest {
        // Arrange
        val repo = FakeCredentialRepository()
        repo.put(sample("a"))
        repo.put(sample("b"))
        val provider = StubProvider(initiallyHasKey = true)
        val detected = FakeDetectedFieldRepository()
        val useCase = ClearVaultUseCase(repo, provider, detected)

        // Act
        val result = useCase()

        // Assert
        assertThat(result.isSuccess).isTrue()
        assertThat(repo.snapshot()).isEmpty()
        assertThat(provider.hasKey()).isFalse()
        assertThat(provider.deleteCallCount).isEqualTo(1)
    }

    @Test
    fun invoke_alsoClearsDetectedFields() = runTest {
        // Issue #67 Phase 2 (design.md §12.4): the Vault clear flow
        // must wipe detected_fields alongside the credentials table so
        // a leftover suggestion history does not survive the user's
        // "delete everything" intent.
        val repo = FakeCredentialRepository().apply { put(sample("a")) }
        val provider = StubProvider(initiallyHasKey = true)
        val detected = FakeDetectedFieldRepository().apply {
            put(DetectedField("com.example.a", "loginEmail", DetectedFieldSource.ResourceId, 100L))
            put(DetectedField("com.example.b", "password", DetectedFieldSource.AutofillHints, 200L))
        }
        val useCase = ClearVaultUseCase(repo, provider, detected)

        val result = useCase()

        assertThat(result.isSuccess).isTrue()
        // Both per-package Flow snapshots must be empty afterwards.
        assertThat(detected.observeRecentByPackage("com.example.a", 10).first()).isEmpty()
        assertThat(detected.observeRecentByPackage("com.example.b", 10).first()).isEmpty()
    }

    @Test
    fun invoke_returnsStorageFailure_andSkipsKeystore_whenRepositoryThrows() = runTest {
        // Req 7.7: when the DB DELETE fails, the Keystore alias must
        // remain intact -- the user's data is still encrypted under it
        // and would be unrecoverable if we dropped the alias.
        val repo = ThrowingFakeRepository(throwOnClear = true)
        val provider = StubProvider(initiallyHasKey = true)
        val detected = FakeDetectedFieldRepository().apply {
            put(DetectedField("com.example.a", "loginEmail", DetectedFieldSource.ResourceId, 100L))
        }
        val useCase = ClearVaultUseCase(repo, provider, detected)

        val result = useCase()

        assertThat(result.isFailure).isTrue()
        val cause = result.exceptionOrNull()
        assertThat(cause).isInstanceOf(ClearVaultFailure.Storage::class.java)
        assertThat((cause as ClearVaultFailure.Storage).reason).isEqualTo("RuntimeException")
        // Keystore was not touched.
        assertThat(provider.deleteCallCount).isEqualTo(0)
        assertThat(provider.hasKey()).isTrue()
        // detected_fields must also be untouched — the credentials
        // wipe failed, so the clear flow aborted before reaching the
        // Phase 2 step.
        assertThat(detected.observeRecentByPackage("com.example.a", 10).first()).hasSize(1)
    }

    @Test
    fun invoke_returnsKeystoreAliasFailure_afterDbAlreadyCleared() = runTest {
        // Req 7.7: when the deleteKey step fails, the DB is already
        // empty. The retry path just re-runs the use-case
        // (deleteAll on empty is a no-op, deleteKey retries).
        val repo = FakeCredentialRepository()
        repo.put(sample("a"))
        val provider = StubProvider(initiallyHasKey = true, throwOnDelete = true)
        val detected = FakeDetectedFieldRepository()
        val useCase = ClearVaultUseCase(repo, provider, detected)

        val result = useCase()

        assertThat(result.isFailure).isTrue()
        val cause = result.exceptionOrNull()
        assertThat(cause).isInstanceOf(ClearVaultFailure.KeystoreAlias::class.java)
        assertThat((cause as ClearVaultFailure.KeystoreAlias).reason).isEqualTo("RuntimeException")
        // DB was cleared first, then deleteKey threw.
        assertThat(repo.snapshot()).isEmpty()
        // Alias still present because the throw happened before the
        // stub flipped its hasKey flag.
        assertThat(provider.hasKey()).isTrue()
    }

    @Test
    fun invoke_isIdempotent_onSuccessfulRetryAfterKeystoreFailure() = runTest {
        // Req 7.7: retry path. After a Keystore failure, the user taps
        // "retry" which re-runs the use-case. The DB is already empty
        // (deleteAll is a no-op) and the second deleteKey succeeds.
        val repo = FakeCredentialRepository()
        repo.put(sample("a"))
        val provider = StubProvider(initiallyHasKey = true, throwOnDelete = true)
        val detected = FakeDetectedFieldRepository()
        val useCase = ClearVaultUseCase(repo, provider, detected)

        useCase() // first attempt: Keystore failure
        provider.flipToSuccess()
        val retry = useCase()

        assertThat(retry.isSuccess).isTrue()
        assertThat(repo.snapshot()).isEmpty()
        assertThat(provider.hasKey()).isFalse()
    }

    @Test
    fun invoke_detectedFieldsFailure_doesNotAbort_andKeystoreStillCleared() = runTest {
        // The detected_fields wipe is best-effort: a failure must not
        // prevent the Keystore alias from being dropped, otherwise a
        // transient SQLite issue on the (relatively unimportant) Phase
        // 2 table would block the user's primary clear intent.
        val repo = FakeCredentialRepository().apply { put(sample("a")) }
        val provider = StubProvider(initiallyHasKey = true)
        val detected = ThrowingDetectedFieldRepository()
        val useCase = ClearVaultUseCase(repo, provider, detected)

        val result = useCase()

        assertThat(result.isSuccess).isTrue()
        assertThat(repo.snapshot()).isEmpty()
        assertThat(provider.hasKey()).isFalse()
    }

    // ---- helpers ---------------------------------------------------------

    /**
     * KeystoreKeyProvider double that records call counts and can be
     * configured to throw on `deleteKey()`.
     */
    private class StubProvider(
        initiallyHasKey: Boolean,
        private var throwOnDelete: Boolean = false,
    ) : KeystoreKeyProvider() {
        private var alive: Boolean = initiallyHasKey
        var deleteCallCount: Int = 0
            private set

        override fun hasKey(): Boolean = alive

        override fun deleteKey() {
            deleteCallCount += 1
            if (throwOnDelete) {
                throw RuntimeException("simulated KeyStoreException")
            }
            alive = false
        }

        fun flipToSuccess() {
            throwOnDelete = false
        }
    }

    /**
     * FakeCredentialRepository extension that makes `clearAll` throw. We
     * subclass instead of mocking so the rest of the repository surface
     * keeps working transparently.
     */
    private class ThrowingFakeRepository(
        private val throwOnClear: Boolean,
    ) : io.github.hitoshiichikawa.keynest.domain.repository.CredentialRepository by FakeCredentialRepository() {
        override suspend fun clearAll() {
            if (throwOnClear) throw RuntimeException("simulated SQLite failure")
        }
    }

    /**
     * DetectedFieldRepository whose `deleteAll()` throws. Used to
     * verify the ClearVaultUseCase tolerates a Phase 2 wipe failure
     * (the credentials wipe is the authoritative step).
     */
    private class ThrowingDetectedFieldRepository :
        io.github.hitoshiichikawa.keynest.domain.repository.DetectedFieldRepository by FakeDetectedFieldRepository() {
        override suspend fun deleteAll() {
            throw RuntimeException("simulated SQLite failure on detected_fields")
        }
    }

    private fun sample(label: String) = EncryptedCredentialRecord(
        id = CredentialId(0L),
        packageName = "com.example.$label",
        username = "user-$label",
        label = label,
        passwordCiphertext = byteArrayOf(0x01, 0x02, 0x03),
        passwordIv = ByteArray(12) { 0x10.toByte() },
        signatureSha256 = SigningHash(ByteArray(32) { 0x20.toByte() }),
        signatureCapturedAt = 1000L,
        createdAt = 0L,
        updatedAt = 0L,
    )
}
