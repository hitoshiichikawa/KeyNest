package io.github.hitoshiichikawa.keynest.credentialprovider.registration

import com.google.common.truth.Truth.assertThat
import java.security.MessageDigest
import org.junit.Test

/**
 * Unit tests for [AuthenticatorDataBuilder] (Issue #99 T-04 / design §4.3 / §7.1 / §7.2).
 *
 * Verifies the bytewise layout required by WebAuthn §6.1 and §6.5.1:
 *   - rpIdHash (32 B SHA-256)
 *   - flags (1 B; registration uses 0x45 = UP|UV|AT)
 *   - signCount (4 B big-endian, initially 0)
 *   - attestedCredentialData: AAGUID(16) || credLen(2 BE) || credId(N) || cose(M)
 *
 * Tests are pure-JVM (`MessageDigest` is JCE standard). No Robolectric needed.
 */
class AuthenticatorDataBuilderTest {

    @Test
    fun rpIdHash_matchesSha256OfUtf8RpId() {
        val rpId = "example.com"
        val expected = MessageDigest.getInstance("SHA-256")
            .digest(rpId.toByteArray(Charsets.UTF_8))

        val actual = AuthenticatorDataBuilder.rpIdHash(rpId)

        assertThat(actual).hasLength(32)
        assertThat(actual).isEqualTo(expected)
    }

    @Test
    fun build_layout_offsetsMatchSpec() {
        val rpIdHash = ByteArray(32) { it.toByte() }
        // Build a fixed attestedCredentialData = AAGUID(16) || credLen(2)=32 ||
        //   credId(32, filled 0x55) || cose(28, filled 0xAA) — 78 bytes total.
        val aaguid = KeynestAaguid.bytes()
        val credId = ByteArray(32) { 0x55.toByte() }
        val cose = ByteArray(28) { 0xAA.toByte() }
        val attested = AuthenticatorDataBuilder.attestedCredentialData(aaguid, credId, cose)

        val actual = AuthenticatorDataBuilder.build(
            rpIdHash = rpIdHash,
            flags = 0x45,
            signCount = 0,
            attestedCredentialData = attested,
        )

        // Total size: 32 + 1 + 4 + 78 = 115.
        assertThat(actual.size).isEqualTo(32 + 1 + 4 + attested.size)
        assertThat(actual.copyOfRange(0, 32)).isEqualTo(rpIdHash)
        assertThat(actual[32]).isEqualTo(0x45.toByte())
        assertThat(actual.copyOfRange(33, 37))
            .isEqualTo(byteArrayOf(0x00, 0x00, 0x00, 0x00))
        assertThat(actual.copyOfRange(37, actual.size)).isEqualTo(attested)
    }

    @Test
    fun build_flags0x45_forRegistration() {
        val combined = (AuthenticatorDataBuilder.FLAG_UP.toInt() or
            AuthenticatorDataBuilder.FLAG_UV.toInt() or
            AuthenticatorDataBuilder.FLAG_AT.toInt()).toByte()
        assertThat(combined).isEqualTo(0x45.toByte())
    }

    @Test
    fun build_signCount_4BytesBigEndian_initiallyZero() {
        val rpIdHash = ByteArray(32)
        val attested = AuthenticatorDataBuilder.attestedCredentialData(
            aaguid = KeynestAaguid.bytes(),
            credentialId = ByteArray(1) { 0x01 },
            publicKeyCose = byteArrayOf(),
        )

        val zero = AuthenticatorDataBuilder.build(rpIdHash, 0x45, 0, attested)
        assertThat(zero.copyOfRange(33, 37)).isEqualTo(byteArrayOf(0, 0, 0, 0))

        val one = AuthenticatorDataBuilder.build(rpIdHash, 0x45, 1, attested)
        assertThat(one.copyOfRange(33, 37)).isEqualTo(byteArrayOf(0x00, 0x00, 0x00, 0x01))

        val big = AuthenticatorDataBuilder.build(rpIdHash, 0x45, 0x12345678, attested)
        assertThat(big.copyOfRange(33, 37))
            .isEqualTo(byteArrayOf(0x12, 0x34, 0x56, 0x78))
    }

    @Test
    fun attestedCredentialData_layout_aaguidThenLenThenIdThenCose() {
        val aaguid = KeynestAaguid.bytes()
        val credId = ByteArray(32) { 0x55.toByte() }
        val cose = ByteArray(77) { 0xCC.toByte() }

        val actual = AuthenticatorDataBuilder.attestedCredentialData(aaguid, credId, cose)

        assertThat(actual.size).isEqualTo(16 + 2 + 32 + 77)
        assertThat(actual.copyOfRange(0, 16)).isEqualTo(aaguid)
        assertThat(actual.copyOfRange(16, 18)).isEqualTo(byteArrayOf(0x00, 0x20)) // 32
        assertThat(actual.copyOfRange(18, 50)).isEqualTo(credId)
        assertThat(actual.copyOfRange(50, actual.size)).isEqualTo(cose)
    }

    @Test
    fun attestedCredentialData_credentialIdLength_isBigEndian2Bytes() {
        val aaguid = KeynestAaguid.bytes()
        val cose = byteArrayOf()

        val len32 = AuthenticatorDataBuilder.attestedCredentialData(
            aaguid, ByteArray(32), cose,
        )
        assertThat(len32.copyOfRange(16, 18)).isEqualTo(byteArrayOf(0x00, 0x20))

        val len256 = AuthenticatorDataBuilder.attestedCredentialData(
            aaguid, ByteArray(256), cose,
        )
        assertThat(len256.copyOfRange(16, 18)).isEqualTo(byteArrayOf(0x01, 0x00))

        val len1023 = AuthenticatorDataBuilder.attestedCredentialData(
            aaguid, ByteArray(1023), cose,
        )
        assertThat(len1023.copyOfRange(16, 18))
            .isEqualTo(byteArrayOf(0x03, 0xFF.toByte()))
    }

    @Test
    fun attestedCredentialData_throwsOnAaguidSize() {
        val cose = byteArrayOf()
        val credId = ByteArray(32)

        val tooShort = runCatching {
            AuthenticatorDataBuilder.attestedCredentialData(ByteArray(15), credId, cose)
        }.exceptionOrNull()
        assertThat(tooShort).isInstanceOf(IllegalArgumentException::class.java)

        val tooLong = runCatching {
            AuthenticatorDataBuilder.attestedCredentialData(ByteArray(17), credId, cose)
        }.exceptionOrNull()
        assertThat(tooLong).isInstanceOf(IllegalArgumentException::class.java)
    }

    @Test
    fun attestedCredentialData_throwsOnCredentialIdSizeOutOfRange() {
        val aaguid = KeynestAaguid.bytes()
        val cose = byteArrayOf()

        val zero = runCatching {
            AuthenticatorDataBuilder.attestedCredentialData(aaguid, ByteArray(0), cose)
        }.exceptionOrNull()
        assertThat(zero).isInstanceOf(IllegalArgumentException::class.java)

        val tooBig = runCatching {
            AuthenticatorDataBuilder.attestedCredentialData(aaguid, ByteArray(1024), cose)
        }.exceptionOrNull()
        assertThat(tooBig).isInstanceOf(IllegalArgumentException::class.java)
    }

    @Test
    fun build_withoutAttestedCredentialData_omitsItAndAtFlag() {
        // Caller is responsible for clearing the AT bit when omitting attested data;
        // the builder simply appends the body without enforcing flag consistency.
        val rpIdHash = ByteArray(32)
        val flagsNoAt = (AuthenticatorDataBuilder.FLAG_UP.toInt() or
            AuthenticatorDataBuilder.FLAG_UV.toInt()).toByte()

        val actual = AuthenticatorDataBuilder.build(
            rpIdHash = rpIdHash,
            flags = flagsNoAt,
            signCount = 0,
            attestedCredentialData = null,
        )

        assertThat(actual.size).isEqualTo(32 + 1 + 4)
        assertThat(actual[32]).isEqualTo(0x05.toByte())
        assertThat(flagsNoAt.toInt() and AuthenticatorDataBuilder.FLAG_AT.toInt()).isEqualTo(0)
    }

    @Test
    fun build_extensions_null_omitsTrailingBytes() {
        val rpIdHash = ByteArray(32)
        val attested = AuthenticatorDataBuilder.attestedCredentialData(
            aaguid = KeynestAaguid.bytes(),
            credentialId = ByteArray(1) { 0x09 },
            publicKeyCose = byteArrayOf(0x00, 0x00),
        )

        val actual = AuthenticatorDataBuilder.build(
            rpIdHash = rpIdHash,
            flags = 0x45,
            signCount = 0,
            attestedCredentialData = attested,
            extensions = null,
        )

        assertThat(actual.size).isEqualTo(32 + 1 + 4 + attested.size)
    }

    @Test
    fun build_throwsOnRpIdHashSize() {
        val tooShort = runCatching {
            AuthenticatorDataBuilder.build(
                rpIdHash = ByteArray(31),
                flags = 0x45,
                signCount = 0,
                attestedCredentialData = null,
            )
        }.exceptionOrNull()
        assertThat(tooShort).isInstanceOf(IllegalArgumentException::class.java)

        val tooLong = runCatching {
            AuthenticatorDataBuilder.build(
                rpIdHash = ByteArray(33),
                flags = 0x45,
                signCount = 0,
                attestedCredentialData = null,
            )
        }.exceptionOrNull()
        assertThat(tooLong).isInstanceOf(IllegalArgumentException::class.java)
    }
}
