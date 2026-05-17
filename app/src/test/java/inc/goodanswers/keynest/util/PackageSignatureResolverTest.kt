package inc.goodanswers.keynest.util

import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.content.pm.Signature
import android.content.pm.SigningInfo
import android.os.Build
import inc.goodanswers.keynest.domain.model.SigningHash
import com.google.common.truth.Truth.assertThat
import io.mockk.every
import io.mockk.mockk
import org.junit.Test
import java.security.MessageDigest

/**
 * Behaviour of [PackageSignatureResolver]. Covers Req 2.1, 2.2, 2.3, 4.1
 * across both SDK code paths.
 *
 * PackageManager is a pure interface from the resolver's perspective so it is
 * mocked with mockk - no Robolectric runtime required, just a plain JVM
 * JUnit4 test.
 *
 * NOTE on [Signature] mocking (Issue #56):
 * Under Plain JVM unit tests, `android.content.pm.Signature` is supplied by
 * the android.jar stub which contains no real implementation - in particular
 * `Signature.toByteArray()` returns null instead of the certificate DER. We
 * therefore build each [Signature] through mockk so that `toByteArray()`
 * deterministically returns the test's signer bytes. The product code under
 * test still computes the canonical SHA-256 over those bytes itself; the mock
 * only replaces the inaccessible-on-JVM accessor. See [signatureOf].
 */
class PackageSignatureResolverTest {

    // --- API 28+ branch (GET_SIGNING_CERTIFICATES) ------------------------

    @Test
    fun resolveSha256_api28_singleSigner_returnsCanonicalHash() {
        // Arrange
        val signerBytes = "MOCK_CERT_DER".toByteArray()
        val signingInfo = mockk<SigningInfo>(relaxed = true)
        every { signingInfo.hasMultipleSigners() } returns false
        every { signingInfo.signingCertificateHistory } returns arrayOf(signatureOf(signerBytes))
        every { signingInfo.apkContentsSigners } returns arrayOf(signatureOf(signerBytes))

        val info = PackageInfo().apply { this.signingInfo = signingInfo }
        val pm = mockk<PackageManager> {
            every { getPackageInfo("com.example.target", PackageManager.GET_SIGNING_CERTIFICATES) } returns info
        }
        val resolver = PackageSignatureResolver(pm, sdkVersion = Build.VERSION_CODES.P)

        // Act
        val hash = resolver.resolveSha256("com.example.target")

        // Assert: canonical = sha256(sha256(signerBytes))
        val perSigner = MessageDigest.getInstance("SHA-256").digest(signerBytes)
        val expected = MessageDigest.getInstance("SHA-256").digest(perSigner)
        assertThat(hash).isNotNull()
        assertThat(hash).isEqualTo(SigningHash(expected))
    }

    @Test
    fun resolveSha256_api28_multipleSigners_isOrderIndependent() {
        // Arrange: two different signers, presented in two different orders
        val a = signatureOf("CERT_A".toByteArray())
        val b = signatureOf("CERT_B".toByteArray())
        val aPrime = signatureOf("CERT_A".toByteArray())
        val bPrime = signatureOf("CERT_B".toByteArray())

        val pm1 = pmWithSigningInfoMultiSigners(arrayOf(a, b))
        val pm2 = pmWithSigningInfoMultiSigners(arrayOf(bPrime, aPrime))

        // Act
        val h1 = PackageSignatureResolver(pm1, Build.VERSION_CODES.P).resolveSha256("pkg")
        val h2 = PackageSignatureResolver(pm2, Build.VERSION_CODES.P).resolveSha256("pkg")

        // Assert
        assertThat(h1).isEqualTo(h2)
    }

    @Test
    fun resolveSha256_api28_uninstalledPackage_returnsNull() {
        // Arrange
        val pm = mockk<PackageManager> {
            every {
                getPackageInfo("com.missing", PackageManager.GET_SIGNING_CERTIFICATES)
            } throws PackageManager.NameNotFoundException()
        }

        // Act
        val hash = PackageSignatureResolver(pm, Build.VERSION_CODES.P).resolveSha256("com.missing")

        // Assert
        assertThat(hash).isNull()
    }

    // --- API 26-27 branch (GET_SIGNATURES) --------------------------------

    @Test
    @Suppress("DEPRECATION")
    fun resolveSha256_api26_singleSigner_returnsHash() {
        // Arrange
        val signer = signatureOf("MOCK_CERT_DER".toByteArray())
        val info = PackageInfo().apply { signatures = arrayOf(signer) }
        val pm = mockk<PackageManager> {
            every { getPackageInfo("com.example.target", PackageManager.GET_SIGNATURES) } returns info
        }
        val resolver = PackageSignatureResolver(pm, sdkVersion = Build.VERSION_CODES.O)

        // Act
        val hash = resolver.resolveSha256("com.example.target")

        // Assert
        assertThat(hash).isNotNull()
    }

    @Test
    @Suppress("DEPRECATION")
    fun resolveSha256_api26_uninstalledPackage_returnsNull() {
        val pm = mockk<PackageManager> {
            every {
                getPackageInfo("com.missing", PackageManager.GET_SIGNATURES)
            } throws PackageManager.NameNotFoundException()
        }
        val hash = PackageSignatureResolver(pm, Build.VERSION_CODES.O).resolveSha256("com.missing")
        assertThat(hash).isNull()
    }

    @Test
    @Suppress("DEPRECATION")
    fun resolveSha256_api26_emptySignatures_returnsNull() {
        val info = PackageInfo().apply { signatures = emptyArray() }
        val pm = mockk<PackageManager> {
            every { getPackageInfo("com.empty", PackageManager.GET_SIGNATURES) } returns info
        }
        val hash = PackageSignatureResolver(pm, Build.VERSION_CODES.O).resolveSha256("com.empty")
        assertThat(hash).isNull()
    }

    // --- Re-resolve semantics (Req 2.3) ----------------------------------

    @Test
    fun resolveSha256_isReRunnable_pickingUpUpdatedSignerEachTime() {
        // Arrange: PackageManager will return a different signer on the 2nd call,
        // simulating an app update with a rotated signing certificate.
        val initial = signatureOf("INITIAL_CERT".toByteArray())
        val rotated = signatureOf("ROTATED_CERT".toByteArray())

        val infoInitial = PackageInfo().apply {
            signingInfo = mockk(relaxed = true) {
                every { hasMultipleSigners() } returns false
                every { signingCertificateHistory } returns arrayOf(initial)
                every { apkContentsSigners } returns arrayOf(initial)
            }
        }
        val infoRotated = PackageInfo().apply {
            signingInfo = mockk(relaxed = true) {
                every { hasMultipleSigners() } returns false
                every { signingCertificateHistory } returns arrayOf(rotated)
                every { apkContentsSigners } returns arrayOf(rotated)
            }
        }
        val pm = mockk<PackageManager> {
            every {
                getPackageInfo("com.rotate", PackageManager.GET_SIGNING_CERTIFICATES)
            } returnsMany listOf(infoInitial, infoRotated)
        }
        val resolver = PackageSignatureResolver(pm, Build.VERSION_CODES.P)

        // Act
        val first = resolver.resolveSha256("com.rotate")
        val second = resolver.resolveSha256("com.rotate")

        // Assert: re-resolve picks up the rotated cert
        assertThat(first).isNotEqualTo(second)
    }

    // --- helpers ----------------------------------------------------------

    /**
     * Builds a [Signature] mock whose `toByteArray()` returns [bytes].
     *
     * Required because the android.jar stub used by Plain JVM unit tests does
     * not implement [Signature.toByteArray] (returns null), which would NPE
     * the SHA-256 path in [PackageSignatureResolver]. The mock is intentionally
     * minimal: we only stub the accessor the resolver actually calls so the
     * hashing logic under test is exercised end-to-end against real input
     * bytes (see Issue #56 / Req 4.1 - solution chosen to avoid extra runtime
     * dependencies).
     */
    private fun signatureOf(bytes: ByteArray): Signature {
        return mockk<Signature>(relaxed = true) {
            every { toByteArray() } returns bytes
        }
    }

    private fun pmWithSigningInfoMultiSigners(signers: Array<Signature>): PackageManager {
        val signingInfo = mockk<SigningInfo>(relaxed = true) {
            every { hasMultipleSigners() } returns true
            every { apkContentsSigners } returns signers
        }
        val info = PackageInfo().apply { this.signingInfo = signingInfo }
        return mockk {
            every { getPackageInfo("pkg", PackageManager.GET_SIGNING_CERTIFICATES) } returns info
        }
    }
}
