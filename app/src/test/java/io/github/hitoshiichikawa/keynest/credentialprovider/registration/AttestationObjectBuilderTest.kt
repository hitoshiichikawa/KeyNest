package io.github.hitoshiichikawa.keynest.credentialprovider.registration

import com.google.common.truth.Truth.assertThat
import org.junit.Test

/**
 * Unit tests for [AttestationObjectBuilder] (Issue #99 T-05 / design §4.6 / §7.3).
 *
 * Verifies the bytewise layout of the `fmt = "none"` attestation object
 * (WebAuthn Level 2 §6.5.4) and the WebAuthn-spec key ordering
 * ("fmt" → "attStmt" → "authData", not alphabetical).
 *
 * The fixed leading byte pattern through "authData" is 28 bytes:
 *   0xA3 0x63 'f' 'm' 't' 0x64 'n' 'o' 'n' 'e'
 *   0x67 'a' 't' 't' 'S' 't' 'm' 't' 0xA0
 *   0x68 'a' 'u' 't' 'h' 'D' 'a' 't' 'a'
 */
class AttestationObjectBuilderTest {

    private val expectedPrefixThroughAuthData = byteArrayOf(
        0xA3.toByte(),                                                  // map(3)
        0x63, 0x66, 0x6D, 0x74,                                         // tstr(3) "fmt"
        0x64, 0x6E, 0x6F, 0x6E, 0x65,                                   // tstr(4) "none"
        0x67, 0x61, 0x74, 0x74, 0x53, 0x74, 0x6D, 0x74,                 // tstr(7) "attStmt"
        0xA0.toByte(),                                                  // map(0) = {}
        0x68, 0x61, 0x75, 0x74, 0x68, 0x44, 0x61, 0x74, 0x61,           // tstr(8) "authData"
    )

    @Test
    fun buildFormatNone_bytewise_prefixMatchesWebAuthnLayout() {
        // 37-byte dummy authData → bstr header `0x58 0x25` (1-byte length, 37 = 0x25).
        val authData = ByteArray(37) { it.toByte() }

        val actual = AttestationObjectBuilder.buildFormatNone(authData)

        val prefix = actual.copyOfRange(0, expectedPrefixThroughAuthData.size)
        assertThat(prefix).isEqualTo(expectedPrefixThroughAuthData)

        // Right after "authData" text string comes the byte-string header.
        val headerStart = expectedPrefixThroughAuthData.size
        assertThat(actual[headerStart]).isEqualTo(0x58.toByte())
        assertThat(actual[headerStart + 1]).isEqualTo(0x25.toByte())

        // Trailing 37 bytes equal the dummy authData.
        val tail = actual.copyOfRange(headerStart + 2, actual.size)
        assertThat(tail).isEqualTo(authData)
        assertThat(actual.size).isEqualTo(headerStart + 2 + 37)
    }

    @Test
    fun buildFormatNone_authDataIsCborByteString_with2ByteHeaderWhenLongerThan255() {
        // 256-byte authData → bstr header `0x59 0x01 0x00`.
        val authData = ByteArray(256) { 0xEE.toByte() }

        val actual = AttestationObjectBuilder.buildFormatNone(authData)

        val headerStart = expectedPrefixThroughAuthData.size
        assertThat(actual[headerStart]).isEqualTo(0x59.toByte())
        assertThat(actual[headerStart + 1]).isEqualTo(0x01.toByte())
        assertThat(actual[headerStart + 2]).isEqualTo(0x00.toByte())

        val tail = actual.copyOfRange(headerStart + 3, actual.size)
        assertThat(tail).isEqualTo(authData)
    }

    @Test
    fun buildFormatNone_mapHeaderIs0xA3() {
        val actual = AttestationObjectBuilder.buildFormatNone(ByteArray(0))
        assertThat(actual[0]).isEqualTo(0xA3.toByte())
    }

    @Test
    fun buildFormatNone_keysAreInWebAuthnOrder_notAlphabetical() {
        val actual = AttestationObjectBuilder.buildFormatNone(ByteArray(0))

        val fmtMarker = byteArrayOf(0x66, 0x6D, 0x74)                         // "fmt"
        val attStmtMarker = byteArrayOf(0x61, 0x74, 0x74, 0x53, 0x74, 0x6D, 0x74) // "attStmt"
        val authDataMarker = byteArrayOf(0x61, 0x75, 0x74, 0x68, 0x44, 0x61, 0x74, 0x61) // "authData"

        val fmtIdx = indexOf(actual, fmtMarker)
        val attStmtIdx = indexOf(actual, attStmtMarker)
        val authDataIdx = indexOf(actual, authDataMarker)

        assertThat(fmtIdx).isAtLeast(0)
        assertThat(attStmtIdx).isAtLeast(0)
        assertThat(authDataIdx).isAtLeast(0)

        // WebAuthn order: fmt < attStmt < authData (alphabetical would put attStmt first).
        assertThat(fmtIdx).isLessThan(attStmtIdx)
        assertThat(attStmtIdx).isLessThan(authDataIdx)
    }

    private fun indexOf(haystack: ByteArray, needle: ByteArray): Int {
        if (needle.isEmpty()) return 0
        outer@ for (i in 0..haystack.size - needle.size) {
            for (j in needle.indices) {
                if (haystack[i + j] != needle[j]) continue@outer
            }
            return i
        }
        return -1
    }
}
