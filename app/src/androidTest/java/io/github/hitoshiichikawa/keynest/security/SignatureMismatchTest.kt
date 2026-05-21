package io.github.hitoshiichikawa.keynest.security

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import io.github.hitoshiichikawa.keynest.domain.model.CredentialId
import io.github.hitoshiichikawa.keynest.domain.model.CredentialSortOrder
import io.github.hitoshiichikawa.keynest.domain.model.EncryptedCredentialRecord
import io.github.hitoshiichikawa.keynest.domain.model.SigningHash
import io.github.hitoshiichikawa.keynest.domain.model.VaultMetadata
import io.github.hitoshiichikawa.keynest.domain.repository.CredentialRepository
import io.github.hitoshiichikawa.keynest.domain.usecase.ResolveAutofillCandidatesUseCase
import io.github.hitoshiichikawa.keynest.util.PackageSignatureResolver
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import org.junit.Test
import org.junit.runner.RunWith

/**
 * T10.2 instrumented audit: signature mismatch / null signature are
 * filtered out before any candidate reaches the AutofillService.
 *
 * Backs Req 4.2, 4.3, 4.4. Uses a stub repository + a stub signature
 * resolver so that the test can simulate signature mismatch deterministically.
 */
@RunWith(AndroidJUnit4::class)
class SignatureMismatchTest {

    private val matching = SigningHash.ofSha256("MATCHING_CERT".toByteArray())
    private val different = SigningHash.ofSha256("DIFFERENT_CERT".toByteArray())

    @Test
    fun candidateFilter_dropsMismatched_andRetainsMatched() = runBlocking {
        val repo = stubRepo(
            listOf(
                rec("alice", matching),
                rec("imposter", different),
                rec("ghost", signature = null),
                rec("bob", matching),
            ),
        )
        val sigResolver = stubResolver(matching)
        val useCase = ResolveAutofillCandidatesUseCase(repo, sigResolver)

        val candidates = useCase("com.example.target")

        assertThat(candidates.map { it.username }).containsExactly("alice", "bob")
    }

    @Test
    fun candidateFilter_returnsEmpty_whenAllAreMismatched() = runBlocking {
        val repo = stubRepo(listOf(rec("imposter", different), rec("ghost", null)))
        val sigResolver = stubResolver(matching)
        val useCase = ResolveAutofillCandidatesUseCase(repo, sigResolver)

        val candidates = useCase("com.example.target")

        assertThat(candidates).isEmpty()
    }

    private fun stubResolver(hash: SigningHash): PackageSignatureResolver =
        object : PackageSignatureResolver(
            InstrumentationRegistry.getInstrumentation().targetContext.packageManager,
        ) {
            override fun resolveSha256(packageName: String): SigningHash? =
                if (packageName == "com.example.target") hash else null
        }

    private fun rec(username: String, signature: SigningHash?) = EncryptedCredentialRecord(
        id = CredentialId(username.hashCode().toLong()),
        packageName = "com.example.target",
        username = username,
        label = "Label-$username",
        passwordCiphertext = byteArrayOf(1, 2, 3),
        passwordIv = ByteArray(12),
        signatureSha256 = signature,
        signatureCapturedAt = if (signature != null) 100L else null,
        createdAt = 0L,
        updatedAt = 0L,
    )

    private fun stubRepo(records: List<EncryptedCredentialRecord>): CredentialRepository =
        object : CredentialRepository {
            override suspend fun save(record: EncryptedCredentialRecord): CredentialId = error("n/a")
            override suspend fun update(record: EncryptedCredentialRecord) = error("n/a")
            override suspend fun delete(id: CredentialId) = error("n/a")
            override suspend fun findByPackage(packageName: String): List<EncryptedCredentialRecord> =
                records.filter { it.packageName == packageName }

            override suspend fun findById(id: CredentialId): EncryptedCredentialRecord? = null
            override fun observeAll(): Flow<List<io.github.hitoshiichikawa.keynest.domain.model.Credential>> = flowOf(emptyList())
            override fun observeBySort(order: CredentialSortOrder): Flow<List<io.github.hitoshiichikawa.keynest.domain.model.Credential>> = flowOf(emptyList())
            override fun observeRecentlyUsed(limit: Int): Flow<List<io.github.hitoshiichikawa.keynest.domain.model.Credential>> = flowOf(emptyList())
            override suspend fun markUsed(id: CredentialId, timestamp: Long) = error("n/a")
            override suspend fun duplicate(sourceId: CredentialId, timestamp: Long): Result<CredentialId> = error("n/a")
            override fun observeMetadata(): Flow<VaultMetadata> = emptyFlow()
            override suspend fun clearAll() = error("n/a")
        }
}
