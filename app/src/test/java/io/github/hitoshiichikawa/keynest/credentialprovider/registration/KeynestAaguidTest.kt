package io.github.hitoshiichikawa.keynest.credentialprovider.registration

import com.google.common.truth.Truth.assertThat
import org.junit.Test

/**
 * Unit tests for [KeynestAaguid] (Issue #99 T-01 / design §3.3).
 *
 * Verifies that the AAGUID emitted into attestedCredentialData matches the
 * confirmed value `2a56cf86-8332-4829-9f2a-e9a4adbc7abe` bytewise, and that
 * [KeynestAaguid.bytes] hands back a defensive copy so a caller's `fill(0)`
 * (the registration ceremony wipes plaintext after `Repository.save`) cannot
 * corrupt the next callee.
 */
class KeynestAaguidTest {

    @Test
    fun bytes_returnsExpectedBigEndian16Bytes() {
        val expected = byteArrayOf(
            0x2A, 0x56, 0xCF.toByte(), 0x86.toByte(),
            0x83.toByte(), 0x32, 0x48, 0x29,
            0x9F.toByte(), 0x2A, 0xE9.toByte(), 0xA4.toByte(),
            0xAD.toByte(), 0xBC.toByte(), 0x7A, 0xBE.toByte(),
        )

        val actual = KeynestAaguid.bytes()

        assertThat(actual).hasLength(16)
        assertThat(actual).isEqualTo(expected)
    }

    @Test
    fun bytes_returnsDefensiveCopy_perCall() {
        val first = KeynestAaguid.bytes()
        val second = KeynestAaguid.bytes()

        // Distinct instances on every call.
        assertThat(first).isNotSameInstanceAs(second)

        // Mutating the first copy must not leak into subsequent calls.
        first.fill(0)
        val third = KeynestAaguid.bytes()
        assertThat(third).isEqualTo(second)
        assertThat(third[0]).isEqualTo(0x2A.toByte())
        assertThat(third[15]).isEqualTo(0xBE.toByte())
    }

    @Test
    fun uuidString_matchesKeynestAaguidConstant() {
        assertThat(KeynestAaguid.UUID_STRING).isEqualTo("2a56cf86-8332-4829-9f2a-e9a4adbc7abe")
    }
}
