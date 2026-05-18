package io.github.hitoshiichikawa.keynest.util

import com.google.common.truth.Truth.assertThat
import org.junit.Test
import java.security.MessageDigest

/**
 * Hex encoding round-trip and boundary tests. Backs Req 2.1 (signing hash
 * encoding) and indirectly NFR 5.1 (preview shortening).
 */
class HexEncodingTest {

    @Test
    fun encode_thenDecode_returnsOriginalBytes_for32ByteHash() {
        // Arrange: a real 32-byte SHA-256
        val sha = MessageDigest.getInstance("SHA-256").digest("hello".toByteArray())
        assertThat(sha.size).isEqualTo(32)

        // Act
        val hex = HexEncoding.encode(sha)
        val decoded = HexEncoding.decode(hex)

        // Assert: 64-char lowercase hex + round-trip
        assertThat(hex).hasLength(64)
        assertThat(hex).matches("[0-9a-f]{64}")
        assertThat(decoded).isEqualTo(sha)
    }

    @Test
    fun encode_zeroAndMaxBytes_producesExpectedHex() {
        assertThat(HexEncoding.encode(byteArrayOf(0x00))).isEqualTo("00")
        assertThat(HexEncoding.encode(byteArrayOf(0xFF.toByte()))).isEqualTo("ff")
        assertThat(HexEncoding.encode(byteArrayOf(0x0A, 0x1B, 0x2C))).isEqualTo("0a1b2c")
    }

    @Test
    fun encode_emptyBytes_returnsEmptyString() {
        assertThat(HexEncoding.encode(byteArrayOf())).isEqualTo("")
    }

    @Test
    fun decode_acceptsBothCases() {
        assertThat(HexEncoding.decode("Ff")).isEqualTo(byteArrayOf(0xFF.toByte()))
        assertThat(HexEncoding.decode("0AbC")).isEqualTo(byteArrayOf(0x0A, 0xBC.toByte()))
    }

    @Test(expected = IllegalArgumentException::class)
    fun decode_oddLength_throws() {
        HexEncoding.decode("abc")
    }

    @Test(expected = IllegalArgumentException::class)
    fun decode_nonHexCharacter_throws() {
        HexEncoding.decode("zz")
    }
}
