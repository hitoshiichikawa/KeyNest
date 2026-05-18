package io.github.hitoshiichikawa.keynest.perf

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import io.github.hitoshiichikawa.keynest.di.ServiceLocator
import io.github.hitoshiichikawa.keynest.domain.model.SigningHash
import io.github.hitoshiichikawa.keynest.domain.usecase.NewCredentialInput
import io.github.hitoshiichikawa.keynest.util.PackageSignatureResolver
import com.google.common.truth.Truth.assertThat
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.runBlocking
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * T10.4 performance test. Backs NFR 2.1 (onFillRequest -> FillResponse
 * median latency <= 300ms) and NFR 2.2 (no decryption inside the locked
 * path).
 *
 * Strategy:
 * - Pre-populate 100 credentials.
 * - Run [ResolveAutofillCandidatesUseCase] 50 times against a known
 *   package name (with stubbed signature so all candidates match) and
 *   record per-call latency in nanoseconds.
 * - Assert median <= 300ms and p95 <= 600ms (matches design.md OQ-6).
 *
 * Sub-path of onFillRequest covered: assist parsing and FillResponse
 * building are excluded because they require an AssistStructure fixture
 * that is difficult to construct outside a real Android UI. The
 * candidate-resolution path is the most expensive single step
 * (DAO read + signature filtering), so satisfying NFR 2.1 here is the
 * meaningful subset.
 */
@RunWith(AndroidJUnit4::class)
class FillRequestLatencyTest {

    private val targetPackage = "com.example.target"
    private val matchingHash = SigningHash.ofSha256("MATCHING".toByteArray())

    @Before
    fun setUp() {
        ServiceLocator.initialize(InstrumentationRegistry.getInstrumentation().targetContext)
    }

    @Test
    fun resolveCandidates_medianLatency_within300ms_for100Credentials() = runBlocking {
        // Arrange: 100 credentials, all with the matching signing hash.
        val save = ServiceLocator.saveCredentialUseCase
        for (i in 0 until 100) {
            save(
                NewCredentialInput(
                    packageName = targetPackage,
                    username = "user-$i",
                    password = "pw-$i".toCharArray(),
                    label = "Label-$i",
                ),
            ).getOrThrow()
        }

        // The signature resolver currently returns null for an uninstalled
        // package, so swap in a fake that pretends the test signature
        // matches. We don't have setter access on ServiceLocator's lazy
        // fields, but we can construct an ad-hoc use case via the same
        // repository.
        val sigResolver = mockk<PackageSignatureResolver>().also {
            every { it.resolveSha256(targetPackage) } returns matchingHash
        }
        // Backfill all rows with the matching hash so they pass the filter.
        for (rec in ServiceLocator.credentialRepository.findByPackage(targetPackage)) {
            ServiceLocator.credentialRepository.update(
                rec.copy(signatureSha256 = matchingHash, signatureCapturedAt = 0L),
            )
        }
        val useCase = io.github.hitoshiichikawa.keynest.domain.usecase.ResolveAutofillCandidatesUseCase(
            ServiceLocator.credentialRepository,
            sigResolver,
        )

        // Warm up
        repeat(5) { useCase(targetPackage) }

        // Measure 50 invocations
        val timings = LongArray(50)
        for (i in 0 until 50) {
            val start = System.nanoTime()
            useCase(targetPackage)
            timings[i] = System.nanoTime() - start
        }

        timings.sort()
        val medianNs = timings[timings.size / 2]
        val p95Ns = timings[(timings.size * 0.95).toInt()]
        val medianMs = medianNs / 1_000_000.0
        val p95Ms = p95Ns / 1_000_000.0

        // Per design.md OQ-6: median <= 300ms, p95 <= 600ms.
        assertThat(medianMs).isLessThan(300.0)
        assertThat(p95Ms).isLessThan(600.0)
    }
}
