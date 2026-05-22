package io.github.hitoshiichikawa.keynest.credentialprovider.authentication

import com.google.common.truth.Truth.assertThat
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.security.KeyPairGenerator
import java.security.MessageDigest
import java.security.Signature
import java.security.interfaces.ECPublicKey
import java.security.spec.ECGenParameterSpec
import org.junit.Test

/**
 * Pure-JVM unit tests for [PasskeyAssertion] (Issue #100 T-01 / design §3.3
 * / §4.2 / §7).
 *
 * Verifies:
 *  - `authenticatorData` bytewise layout (rpIdHash / flags / signCount /
 *    全長 37 byte) — req 3.2 / 決定 1 / 決定 2
 *  - ES256 (P-256) 署名が同テスト内で生成した public key で verify できる
 *    こと、ASN.1 DER 形式 (JCE 標準) — req 3.5 / req 4.2
 *  - 署名対象が `authenticatorData || SHA-256(clientDataJson)` — req 3.5
 *  - signCount overflow / malformed PKCS#8 で `PasskeyAssertionException`
 *
 * `KeyPairGenerator.getInstance("EC")` は JCE 標準 (`SunEC` on the JVM)
 * で利用可能なため Robolectric 不要。
 */
class PasskeyAssertionTest {

    @Test
    fun sign_authenticatorData_rpIdHash_matchesSha256OfUtf8RpId() {
        val rpId = "example.com"
        val expected = MessageDigest.getInstance("SHA-256")
            .digest(rpId.toByteArray(Charsets.UTF_8))

        val result = signSample(rpId = rpId)

        assertThat(result.authenticatorData.copyOfRange(0, 32)).isEqualTo(expected)
    }

    @Test
    fun sign_authenticatorData_flagsIs0x05_atIsZero_edIsZero() {
        val result = signSample()

        val flags = result.authenticatorData[32].toInt() and 0xFF
        assertThat(flags).isEqualTo(0x05)
        // AT bit (0x40) must be 0 — assertion 側は attestedCredentialData なし
        assertThat(flags and 0x40).isEqualTo(0)
        // ED bit (0x80) must be 0 — extensions なし (決定 2)
        assertThat(flags and 0x80).isEqualTo(0)
    }

    @Test
    fun sign_authenticatorData_signCountIs4BytesBigEndian() {
        // 0x00000001
        val one = signSample(signCount = 1L).authenticatorData.copyOfRange(33, 37)
        assertThat(one).isEqualTo(byteArrayOf(0x00, 0x00, 0x00, 0x01))

        // 0x00000002
        val two = signSample(signCount = 2L).authenticatorData.copyOfRange(33, 37)
        assertThat(two).isEqualTo(byteArrayOf(0x00, 0x00, 0x00, 0x02))

        // 0xFFFFFFFF (boundary)
        val max = signSample(signCount = 0xFFFF_FFFFL).authenticatorData.copyOfRange(33, 37)
        assertThat(max).isEqualTo(
            byteArrayOf(0xFF.toByte(), 0xFF.toByte(), 0xFF.toByte(), 0xFF.toByte()),
        )
    }

    @Test
    fun sign_authenticatorData_lengthIs37Bytes() {
        val result = signSample()

        assertThat(result.authenticatorData.size).isEqualTo(37)
        assertThat(PasskeyAssertion.AUTHENTICATOR_DATA_LENGTH).isEqualTo(37)
    }

    @Test
    fun sign_signatureVerifiesWithGeneratedPublicKey_asn1Der() {
        val kp = generateP256KeyPair()
        val rpId = "example.com"
        val clientDataJson = """{"type":"webauthn.get","challenge":"abc","origin":"https://example.com"}"""

        val result = PasskeyAssertion.sign(
            PasskeyAssertionInput(
                rpId = rpId,
                clientDataJson = clientDataJson,
                signCount = 7L,
                privateKeyPkcs8 = kp.privateKeyPkcs8,
            ),
        )

        val clientDataHash = MessageDigest.getInstance("SHA-256")
            .digest(clientDataJson.toByteArray(Charsets.UTF_8))
        val verifier = Signature.getInstance("SHA256withECDSA").apply {
            initVerify(kp.publicKey)
            update(result.authenticatorData)
            update(clientDataHash)
        }
        assertThat(verifier.verify(result.signature)).isTrue()
    }

    @Test
    fun sign_signatureChangesWhenClientDataJsonChanges() {
        val kp = generateP256KeyPair()
        val rpId = "example.com"

        val a = PasskeyAssertion.sign(
            PasskeyAssertionInput(
                rpId = rpId,
                clientDataJson = "{\"a\":1}",
                signCount = 0L,
                privateKeyPkcs8 = kp.privateKeyPkcs8,
            ),
        )
        val b = PasskeyAssertion.sign(
            PasskeyAssertionInput(
                rpId = rpId,
                clientDataJson = "{\"a\":2}",
                signCount = 0L,
                privateKeyPkcs8 = kp.privateKeyPkcs8,
            ),
        )

        // Same authenticatorData (same rpId / flags / signCount) but different
        // signature input because clientDataJson differs.
        assertThat(a.authenticatorData).isEqualTo(b.authenticatorData)
        assertThat(a.signature).isNotEqualTo(b.signature)
    }

    @Test
    fun sign_throwsEncoding_whenSignCountIsNegative() {
        val kp = generateP256KeyPair()

        val ex = runCatching {
            PasskeyAssertion.sign(
                PasskeyAssertionInput(
                    rpId = "example.com",
                    clientDataJson = "{}",
                    signCount = -1L,
                    privateKeyPkcs8 = kp.privateKeyPkcs8,
                ),
            )
        }.exceptionOrNull()

        assertThat(ex).isInstanceOf(PasskeyAssertionException.Encoding::class.java)
    }

    @Test
    fun sign_throwsEncoding_whenSignCountOverflowsUnsigned32() {
        val kp = generateP256KeyPair()

        val ex = runCatching {
            PasskeyAssertion.sign(
                PasskeyAssertionInput(
                    rpId = "example.com",
                    clientDataJson = "{}",
                    signCount = 0x1_0000_0000L,
                    privateKeyPkcs8 = kp.privateKeyPkcs8,
                ),
            )
        }.exceptionOrNull()

        assertThat(ex).isInstanceOf(PasskeyAssertionException.Encoding::class.java)
    }

    @Test
    fun sign_throwsSignFailed_whenPrivateKeyPkcs8IsMalformed() {
        val ex = runCatching {
            PasskeyAssertion.sign(
                PasskeyAssertionInput(
                    rpId = "example.com",
                    clientDataJson = "{}",
                    signCount = 0L,
                    privateKeyPkcs8 = byteArrayOf(0x00, 0x01, 0x02, 0x03),
                ),
            )
        }.exceptionOrNull()

        assertThat(ex).isInstanceOf(PasskeyAssertionException.SignFailed::class.java)
    }

    @Test
    fun sign_signCountBigEndianIsConsistentWithByteBuffer() {
        // Cross-check against ByteBuffer big-endian so a future refactor of
        // AuthenticatorDataBuilder can't silently flip endianness.
        val signCount = 0x1234_5678L
        val expected = ByteBuffer.allocate(4)
            .order(ByteOrder.BIG_ENDIAN)
            .putInt(signCount.toInt())
            .array()

        val result = signSample(signCount = signCount)

        assertThat(result.authenticatorData.copyOfRange(33, 37)).isEqualTo(expected)
    }

    // ---- helpers --------------------------------------------------------

    private data class TestKeyPair(
        val privateKeyPkcs8: ByteArray,
        val publicKey: ECPublicKey,
    )

    private fun generateP256KeyPair(): TestKeyPair {
        val gen = KeyPairGenerator.getInstance("EC").apply {
            initialize(ECGenParameterSpec("secp256r1"))
        }
        val kp = gen.generateKeyPair()
        return TestKeyPair(
            privateKeyPkcs8 = kp.private.encoded
                ?: error("EC private key has no PKCS#8 encoding"),
            publicKey = kp.public as ECPublicKey,
        )
    }

    private fun signSample(
        rpId: String = "example.com",
        clientDataJson: String = "{}",
        signCount: Long = 0L,
    ): PasskeyAssertionResult {
        val kp = generateP256KeyPair()
        return PasskeyAssertion.sign(
            PasskeyAssertionInput(
                rpId = rpId,
                clientDataJson = clientDataJson,
                signCount = signCount,
                privateKeyPkcs8 = kp.privateKeyPkcs8,
            ),
        )
    }
}
