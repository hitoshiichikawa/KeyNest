package io.github.hitoshiichikawa.keynest.credentialprovider.registration

import com.google.common.truth.Truth.assertThat
import org.junit.Test

/**
 * Unit tests for [CborWriter] (Issue #99 T-02 / design §4.5 / §7.5).
 *
 * Test vectors come from RFC 8949 Appendix A (Examples of Encoded CBOR Data
 * Items). Coverage is intentionally limited to the primitives the registration
 * ceremony actually uses (uint / nint / byte string / text string / array
 * header / map header) — the writer rejects out-of-contract input rather than
 * silently encoding (defensive guards per design §4.5).
 */
class CborWriterTest {

    @Test
    fun writeUnsignedInt_smallValues() {
        // RFC 8949 Appendix A — encoding boundaries for major type 0.
        assertThat(CborWriter().writeUnsignedInt(0).toByteArray())
            .isEqualTo(byteArrayOf(0x00))
        assertThat(CborWriter().writeUnsignedInt(23).toByteArray())
            .isEqualTo(byteArrayOf(0x17))
        assertThat(CborWriter().writeUnsignedInt(24).toByteArray())
            .isEqualTo(byteArrayOf(0x18, 0x18))
        assertThat(CborWriter().writeUnsignedInt(255).toByteArray())
            .isEqualTo(byteArrayOf(0x18, 0xFF.toByte()))
        assertThat(CborWriter().writeUnsignedInt(256).toByteArray())
            .isEqualTo(byteArrayOf(0x19, 0x01, 0x00))
        assertThat(CborWriter().writeUnsignedInt(65535).toByteArray())
            .isEqualTo(byteArrayOf(0x19, 0xFF.toByte(), 0xFF.toByte()))
        assertThat(CborWriter().writeUnsignedInt(65536).toByteArray())
            .isEqualTo(byteArrayOf(0x1A, 0x00, 0x01, 0x00, 0x00))
    }

    @Test
    fun writeNegativeInt_smallValues() {
        // RFC 8949 §3.1 — major type 1 encodes (-1 - n) as the unsigned argument.
        assertThat(CborWriter().writeNegativeInt(-1).toByteArray())
            .isEqualTo(byteArrayOf(0x20))
        assertThat(CborWriter().writeNegativeInt(-7).toByteArray())
            .isEqualTo(byteArrayOf(0x26)) // major 1, arg 6 = -1 - 6 = -7
        assertThat(CborWriter().writeNegativeInt(-24).toByteArray())
            .isEqualTo(byteArrayOf(0x37))
        assertThat(CborWriter().writeNegativeInt(-25).toByteArray())
            .isEqualTo(byteArrayOf(0x38, 0x18))
        assertThat(CborWriter().writeNegativeInt(-256).toByteArray())
            .isEqualTo(byteArrayOf(0x38, 0xFF.toByte()))
        assertThat(CborWriter().writeNegativeInt(-257).toByteArray())
            .isEqualTo(byteArrayOf(0x39, 0x01, 0x00))
    }

    @Test
    fun writeByteString_empty_and_small_and_257() {
        assertThat(CborWriter().writeByteString(byteArrayOf()).toByteArray())
            .isEqualTo(byteArrayOf(0x40))
        assertThat(CborWriter().writeByteString(byteArrayOf(0xAB.toByte())).toByteArray())
            .isEqualTo(byteArrayOf(0x41, 0xAB.toByte()))

        val payload = ByteArray(257) { 0xCC.toByte() }
        val actual = CborWriter().writeByteString(payload).toByteArray()

        // Header: major 2 (0x40) | 0x19 (2-byte length) = 0x59, then 0x01 0x01 (= 257).
        assertThat(actual.size).isEqualTo(3 + 257)
        assertThat(actual[0]).isEqualTo(0x59.toByte())
        assertThat(actual[1]).isEqualTo(0x01.toByte())
        assertThat(actual[2]).isEqualTo(0x01.toByte())
        assertThat(actual.copyOfRange(3, actual.size)).isEqualTo(payload)
    }

    @Test
    fun writeTextString_utf8_includingNonAscii() {
        assertThat(CborWriter().writeTextString("").toByteArray())
            .isEqualTo(byteArrayOf(0x60))
        assertThat(CborWriter().writeTextString("none").toByteArray())
            .isEqualTo(byteArrayOf(0x64, 0x6E, 0x6F, 0x6E, 0x65))

        // "あ" (U+3042) → UTF-8: 0xE3 0x81 0x82, so 3 bytes total.
        // Header: major 3 (0x60) | length 3 = 0x63.
        assertThat(CborWriter().writeTextString("あ").toByteArray())
            .isEqualTo(byteArrayOf(0x63, 0xE3.toByte(), 0x81.toByte(), 0x82.toByte()))
    }

    @Test
    fun writeMapHeader_emptyAndFiveEntries() {
        assertThat(CborWriter().writeMapHeader(0).toByteArray())
            .isEqualTo(byteArrayOf(0xA0.toByte()))
        assertThat(CborWriter().writeMapHeader(5).toByteArray())
            .isEqualTo(byteArrayOf(0xA5.toByte()))
    }

    @Test
    fun writeArrayHeader_small() {
        assertThat(CborWriter().writeArrayHeader(0).toByteArray())
            .isEqualTo(byteArrayOf(0x80.toByte()))
        assertThat(CborWriter().writeArrayHeader(1).toByteArray())
            .isEqualTo(byteArrayOf(0x81.toByte()))
    }

    @Test
    fun chainedWrites_producesContiguousByteArray() {
        // map(1) { 1: -7 } = 0xA1 0x01 0x26.
        val actual = CborWriter()
            .writeMapHeader(1)
            .writeUnsignedInt(1)
            .writeNegativeInt(-7)
            .toByteArray()

        assertThat(actual).isEqualTo(byteArrayOf(0xA1.toByte(), 0x01, 0x26))
    }

    @Test
    fun negativeInt_throwsForPositiveArgument() {
        runCatching { CborWriter().writeNegativeInt(0) }
            .exceptionOrNull()
            .also { assertThat(it).isInstanceOf(IllegalArgumentException::class.java) }

        runCatching { CborWriter().writeNegativeInt(1) }
            .exceptionOrNull()
            .also { assertThat(it).isInstanceOf(IllegalArgumentException::class.java) }
    }

    @Test
    fun unsignedInt_throwsForNegativeArgument() {
        runCatching { CborWriter().writeUnsignedInt(-1) }
            .exceptionOrNull()
            .also { assertThat(it).isInstanceOf(IllegalArgumentException::class.java) }
    }
}
