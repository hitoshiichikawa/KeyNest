package com.example.keynest.domain.usecase

import com.example.keynest.domain.model.CredentialId
import com.example.keynest.domain.model.EncryptedCredentialRecord
import com.example.keynest.domain.model.SigningHash
import com.example.keynest.util.PackageSignatureResolver
import com.google.common.truth.Truth.assertThat
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Test

/**
 * Behaviour of [ResolveAutofillCandidatesUseCase] - the heart of the
 * signature-matching trust boundary.
 *
 * Covers Req 3.1, 3.5, 4.1, 4.2, 4.3, 4.4, NFR 2.2 (no decryption),
 * NFR 3.2 (no exception escapes).
 */
class ResolveAutofillCandidatesUseCaseTest {

    private val repo = FakeCredentialRepository()
    private val sigResolver = mockk<PackageSignatureResolver>()

    private val matchingHash = SigningHash.ofSha256("MATCHING".toByteArray())
    private val differentHash = SigningHash.ofSha256("DIFFERENT".toByteArray())

    private fun rec(
        username: String,
        signature: SigningHash?,
        pkg: String = "com.example.target",
    ) = EncryptedCredentialRecord(
        id = CredentialId(0L),
        packageName = pkg,
        username = username,
        label = "Label-$username",
        passwordCiphertext = byteArrayOf(1, 2, 3),
        passwordIv = ByteArray(12),
        signatureSha256 = signature,
        signatureCapturedAt = if (signature != null) 100L else null,
        createdAt = 100L,
        updatedAt = 100L,
    )

    // ---- Req 4.2 ---------------------------------------------------------

    @Test
    fun invoke_returnsOnlyMatchingSignatureCredentials() = runTest {
        repo.put(rec("alice", matchingHash))
        repo.put(rec("imposter", differentHash))
        every { sigResolver.resolveSha256("com.example.target") } returns matchingHash
        val useCase = ResolveAutofillCandidatesUseCase(repo, sigResolver)

        val candidates = useCase("com.example.target")

        assertThat(candidates.map { it.username }).containsExactly("alice")
    }

    // ---- Req 4.3 ---------------------------------------------------------

    @Test
    fun invoke_excludesCredentials_withNullSignature() = runTest {
        repo.put(rec("alice", matchingHash))
        repo.put(rec("ghost", signature = null))
        every { sigResolver.resolveSha256("com.example.target") } returns matchingHash
        val useCase = ResolveAutofillCandidatesUseCase(repo, sigResolver)

        val candidates = useCase("com.example.target")

        assertThat(candidates.map { it.username }).containsExactly("alice")
    }

    // ---- Req 4.4 ---------------------------------------------------------

    @Test
    fun invoke_returnsOnlyMatchingMembers_whenMixed() = runTest {
        repo.put(rec("alice", matchingHash))
        repo.put(rec("imposter", differentHash))
        repo.put(rec("ghost", signature = null))
        repo.put(rec("bob", matchingHash))
        every { sigResolver.resolveSha256("com.example.target") } returns matchingHash
        val useCase = ResolveAutofillCandidatesUseCase(repo, sigResolver)

        val candidates = useCase("com.example.target")

        assertThat(candidates.map { it.username }).containsExactly("alice", "bob")
    }

    // ---- NFR 3.2 ---------------------------------------------------------

    @Test
    fun invoke_returnsEmpty_whenNoCredentialsExist() = runTest {
        every { sigResolver.resolveSha256("com.example.target") } returns matchingHash
        val useCase = ResolveAutofillCandidatesUseCase(repo, sigResolver)

        val candidates = useCase("com.example.target")

        assertThat(candidates).isEmpty()
    }

    @Test
    fun invoke_returnsEmpty_whenCallerSignatureCannotBeResolved() = runTest {
        // If we can't even determine the caller's current signature, we
        // must not return anything. Maps to the spirit of Req 4.3 (don't
        // surface credentials when signature trust cannot be established).
        repo.put(rec("alice", matchingHash))
        every { sigResolver.resolveSha256("com.example.target") } returns null
        val useCase = ResolveAutofillCandidatesUseCase(repo, sigResolver)

        val candidates = useCase("com.example.target")

        assertThat(candidates).isEmpty()
    }

    @Test
    fun invoke_returnsEmpty_whenCallerPackageIsBlank() = runTest {
        val useCase = ResolveAutofillCandidatesUseCase(repo, sigResolver)
        assertThat(useCase("")).isEmpty()
        assertThat(useCase("  ")).isEmpty()
    }

    @Test
    fun invoke_swallowsRepositoryExceptions_returningEmpty() = runTest {
        // NFR 3.2: exceptions from below the use case must not propagate up
        // into the AutofillService.
        val throwingRepo = object : com.example.keynest.domain.repository.CredentialRepository {
            override suspend fun save(record: EncryptedCredentialRecord) = error("boom")
            override suspend fun update(record: EncryptedCredentialRecord) = error("boom")
            override suspend fun delete(id: CredentialId) = error("boom")
            override suspend fun findByPackage(packageName: String): List<EncryptedCredentialRecord> =
                throw IllegalStateException("simulated DB failure")

            override suspend fun findById(id: CredentialId): EncryptedCredentialRecord? = null
            override fun observeAll() = kotlinx.coroutines.flow.flowOf(emptyList<com.example.keynest.domain.model.Credential>())

            // Issue #24: the following stubs were missing on the develop
            // branch (Issue #9 / #10 added them to CredentialRepository
            // but never updated this anonymous fake). Filling them in is
            // mechanically required to compile the test module; none of
            // these methods are exercised by ResolveAutofillCandidatesUseCase,
            // so returning error("boom") preserves the test's original
            // intent of verifying that the use case swallows underlying
            // failures (NFR 3.2). No assertion is relaxed.
            override fun observeBySort(
                order: com.example.keynest.domain.model.CredentialSortOrder,
            ) = kotlinx.coroutines.flow.flowOf(
                emptyList<com.example.keynest.domain.model.Credential>(),
            )

            override fun observeRecentlyUsed(limit: Int) =
                kotlinx.coroutines.flow.flowOf(
                    emptyList<com.example.keynest.domain.model.Credential>(),
                )

            override suspend fun markUsed(id: CredentialId, timestamp: Long) = error("boom")

            override suspend fun duplicate(
                sourceId: CredentialId,
                timestamp: Long,
            ): Result<CredentialId> = Result.failure(IllegalStateException("boom"))

            override fun observeMetadata() = kotlinx.coroutines.flow.flowOf(
                com.example.keynest.domain.model.VaultMetadata(count = 0, latestUpdatedAt = null),
            )

            override suspend fun clearAll() = error("boom")
        }
        every { sigResolver.resolveSha256("com.example.target") } returns matchingHash
        val useCase = ResolveAutofillCandidatesUseCase(throwingRepo, sigResolver)

        val candidates = useCase("com.example.target")

        assertThat(candidates).isEmpty()
    }

    // ---- NFR 2.2 (structural) -------------------------------------------

    @Test
    fun candidates_doNotCarryCiphertextOrIv() = runTest {
        // AutofillCandidate intentionally has NO ciphertext / iv fields. This
        // test pins that contract so future maintainers do not silently add
        // them, which would risk leaking encrypted bytes via the Autofill
        // response path. Compile-time check + runtime assertion.
        repo.put(rec("alice", matchingHash))
        every { sigResolver.resolveSha256("com.example.target") } returns matchingHash
        val useCase = ResolveAutofillCandidatesUseCase(repo, sigResolver)

        val candidates = useCase("com.example.target")

        assertThat(candidates).hasSize(1)
        val fieldNames = AutofillCandidate::class.java.declaredFields.map { it.name }
        assertThat(fieldNames).doesNotContain("passwordCiphertext")
        assertThat(fieldNames).doesNotContain("passwordIv")
    }
}
