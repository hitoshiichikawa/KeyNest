package io.github.hitoshiichikawa.keynest.credentialprovider.registration

import com.google.common.truth.Truth.assertThat
import java.math.BigInteger
import java.security.AlgorithmParameters
import java.security.KeyFactory
import java.security.KeyPairGenerator
import java.security.interfaces.ECPublicKey
import java.security.spec.ECGenParameterSpec
import java.security.spec.ECParameterSpec
import java.security.spec.ECPoint
import java.security.spec.ECPublicKeySpec
import org.junit.Test

/**
 * Unit tests for [CoseKeyEncoder] (Issue #99 T-03 / design §4.4 / §7.4).
 *
 * The bytewise vector below is **structurally pinned** against the COSE_Key
 * layout described in design §7.4. The X coordinate is taken from RFC 8152
 * §C.7.1 (ES256 public key example with `kid = bilbo.baggins@hobbiton.example`)
 * for the value-bearing test (`encodeEs256_bytewise_matchesKnownVector`); the
 * remaining tests construct fresh P-256 keys to verify padding invariants and
 * curve guards, which avoids any dependency on test-vector validity on
 * platform JCE providers.
 *
 * All key reconstruction is done via JCE standard EC `KeyFactory` so the tests
 * stay pure-JVM (no Android / Robolectric SDK download required).
 */
class CoseKeyEncoderTest {

    @Test
    fun encodeEs256_bytewise_matchesKnownVector() {
        // RFC 8152 §C.7.1 — bilbo.baggins@hobbiton.example.
        // Hex coordinates are interpreted unsigned big-endian.
        val xHex = "bac5b11cad8f99f9c72b05cf4b9e26d244dc189f745228255a219a86d6a09eff"
        val yHex = "20138bf82dc1b6d562be0fa54ab7804a3a64b6d72ccfed6b6fb6ed28bbfc117e"
        val publicKey = newP256PublicKey(BigInteger(xHex, 16), BigInteger(yHex, 16))

        val encoded = CoseKeyEncoder.encodeEs256(publicKey)

        // Expected layout (design §7.4):
        //   0xA5                  — map(5)
        //   0x01 0x02             — kty = 2 (EC2)
        //   0x03 0x26             — alg = -7 (ES256)
        //   0x20 0x01             — crv = 1 (P-256)
        //   0x21 0x58 0x20 <x...> — -2 (x), bytes(32)
        //   0x22 0x58 0x20 <y...> — -3 (y), bytes(32)
        val expected = byteArrayOf(
            0xA5.toByte(),
            0x01, 0x02,
            0x03, 0x26,
            0x20, 0x01,
            0x21, 0x58, 0x20,
        ) + hexToBytes(xHex) + byteArrayOf(0x22, 0x58, 0x20) + hexToBytes(yHex)

        assertThat(encoded).isEqualTo(expected)
    }

    @Test
    fun encodeEs256_paddsX_andY_to_32Bytes_evenWhenMsbZero() {
        // Use a freshly-generated P-256 keypair and forcibly clamp the
        // coordinate values to ensure the top bit is zero (so BigInteger
        // returns a strictly < 32-byte unsigned representation). We can't
        // arbitrarily pick a (x, y) and have it lie on P-256, but we can
        // re-roll until we observe at least one MSB-zero coordinate — the
        // contract under test is that *whatever* the BigInteger size is,
        // CoseKeyEncoder always emits 32 bytes for both x and y.
        var key: ECPublicKey? = null
        repeat(64) {
            val candidate = generateP256KeyPair()
            val xLen = candidate.w.affineX.toByteArray().size
            val yLen = candidate.w.affineY.toByteArray().size
            // toByteArray() with sign byte is 33 if MSB=1; we want <=32 so the
            // padding path is exercised. 32 with MSB=0 already exercises strip
            // (raw == 32 but signum > 0); 31-or-less exercises left padding.
            if (xLen < 32 || yLen < 32) {
                key = candidate
                return@repeat
            }
        }
        val sample = key ?: generateP256KeyPair()

        val encoded = CoseKeyEncoder.encodeEs256(sample)

        // Locate the two byte-string headers (0x58 0x20) and assert the
        // following 32 bytes equal the big-endian unsigned coordinate.
        val xHeader = 7 // 0xA5 0x01 0x02 0x03 0x26 0x20 0x01 [0x21] 0x58 0x20 ...
        assertThat(encoded[xHeader]).isEqualTo(0x21.toByte())
        assertThat(encoded[xHeader + 1]).isEqualTo(0x58.toByte())
        assertThat(encoded[xHeader + 2]).isEqualTo(0x20.toByte())

        val xBytes = encoded.copyOfRange(xHeader + 3, xHeader + 3 + 32)
        assertThat(xBytes.size).isEqualTo(32)
        assertThat(BigInteger(1, xBytes)).isEqualTo(sample.w.affineX)

        val yMarker = xHeader + 3 + 32
        assertThat(encoded[yMarker]).isEqualTo(0x22.toByte())
        assertThat(encoded[yMarker + 1]).isEqualTo(0x58.toByte())
        assertThat(encoded[yMarker + 2]).isEqualTo(0x20.toByte())

        val yBytes = encoded.copyOfRange(yMarker + 3, yMarker + 3 + 32)
        assertThat(yBytes.size).isEqualTo(32)
        assertThat(BigInteger(1, yBytes)).isEqualTo(sample.w.affineY)
    }

    @Test
    fun encodeEs256_throwsForNonP256Curve() {
        val gen = KeyPairGenerator.getInstance("EC")
        gen.initialize(ECGenParameterSpec("secp384r1"))
        val nonP256 = gen.generateKeyPair().public as ECPublicKey

        val thrown = runCatching { CoseKeyEncoder.encodeEs256(nonP256) }.exceptionOrNull()

        assertThat(thrown).isInstanceOf(IllegalArgumentException::class.java)
    }

    private fun newP256PublicKey(x: BigInteger, y: BigInteger): ECPublicKey {
        val params = AlgorithmParameters.getInstance("EC").apply {
            init(ECGenParameterSpec("secp256r1"))
        }.getParameterSpec(ECParameterSpec::class.java)
        val spec = ECPublicKeySpec(ECPoint(x, y), params)
        return KeyFactory.getInstance("EC").generatePublic(spec) as ECPublicKey
    }

    private fun generateP256KeyPair(): ECPublicKey {
        val gen = KeyPairGenerator.getInstance("EC")
        gen.initialize(ECGenParameterSpec("secp256r1"))
        return gen.generateKeyPair().public as ECPublicKey
    }

    private fun hexToBytes(hex: String): ByteArray {
        require(hex.length % 2 == 0)
        return ByteArray(hex.length / 2) { i ->
            val high = Character.digit(hex[i * 2], 16)
            val low = Character.digit(hex[i * 2 + 1], 16)
            ((high shl 4) or low).toByte()
        }
    }
}
