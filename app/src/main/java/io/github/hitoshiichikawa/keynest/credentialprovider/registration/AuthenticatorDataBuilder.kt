package io.github.hitoshiichikawa.keynest.credentialprovider.registration

import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.security.MessageDigest

/**
 * WebAuthn Level 2 §6.1 の `authenticatorData` を組み立てる pure-Kotlin ヘルパ
 * (Issue #99 / parent #89 / design §4.3 / §7.1 / §7.2).
 *
 * authenticatorData layout (登録セレモニー時):
 *
 * ```
 * +-------------+--------+------------+-------------------------------+
 * | rpIdHash    | flags  | signCount  | attestedCredentialData (省略可) |
 * | 32 byte     | 1 byte | 4 byte BE  | AAGUID(16) || credLen(2) || ...|
 * +-------------+--------+------------+-------------------------------+
 * ```
 *
 * `flags` の組み立ては呼び出し側の責務:
 * - 登録時:   `FLAG_UP or FLAG_UV or FLAG_AT` = `0x45`
 * - 認証時 (#89 分割案 4): `FLAG_UP or FLAG_UV` = `0x05`
 *
 * 本クラスは `attestedCredentialData = null` のときに AT ビットを自動で抜くような
 * 制御は行わない。呼び出し側が flags と attestedCredentialData の整合を取る。
 */
internal object AuthenticatorDataBuilder {

    /** Bit 0: User Presence。 */
    const val FLAG_UP: Byte = 0x01

    /** Bit 2: User Verified。 */
    const val FLAG_UV: Byte = 0x04

    /** Bit 3: Backup Eligibility (KeyNest は cloud sync 無しのため常に 0)。 */
    const val FLAG_BE: Byte = 0x08

    /** Bit 4: Backup State (KeyNest は cloud sync 無しのため常に 0)。 */
    const val FLAG_BS: Byte = 0x10

    /** Bit 6: attestedCredentialData present。 */
    const val FLAG_AT: Byte = 0x40

    /** Bit 7: extensionData present (Byte 表現で `-0x80` = `0x80` unsigned)。 */
    const val FLAG_ED: Byte = -0x80

    /**
     * `SHA-256(rpId)` の 32 byte。UTF-8 エンコードした上で hashing する (WebAuthn §6.1)。
     */
    fun rpIdHash(rpId: String): ByteArray =
        MessageDigest.getInstance("SHA-256").digest(rpId.toByteArray(Charsets.UTF_8))

    /**
     * `AAGUID(16) || credentialIdLength(2 BE) || credentialId(N) || publicKeyCose(M)` の連結
     * (WebAuthn §6.5.1)。
     *
     * @param aaguid 16 byte の AAGUID。長さが異なる場合 [IllegalArgumentException]
     * @param credentialId 1..1023 byte の credentialId。範囲外なら [IllegalArgumentException]
     * @param publicKeyCose COSE_Key (CBOR map) byte 列
     */
    fun attestedCredentialData(
        aaguid: ByteArray,
        credentialId: ByteArray,
        publicKeyCose: ByteArray,
    ): ByteArray {
        require(aaguid.size == 16) {
            "AAGUID must be 16 bytes, got ${aaguid.size}"
        }
        require(credentialId.size in 1..1023) {
            "credentialId length must be in 1..1023 bytes, got ${credentialId.size}"
        }

        val credLen = ByteBuffer.allocate(2)
            .order(ByteOrder.BIG_ENDIAN)
            .putShort(credentialId.size.toShort())
            .array()

        val out = ByteArrayOutputStream(aaguid.size + 2 + credentialId.size + publicKeyCose.size)
        out.write(aaguid)
        out.write(credLen)
        out.write(credentialId)
        out.write(publicKeyCose)
        return out.toByteArray()
    }

    /**
     * `rpIdHash || flags || signCount || attestedCredentialData? || extensions?` の連結
     * (WebAuthn §6.1)。
     *
     * @param rpIdHash 32 byte SHA-256(rpId)
     * @param flags    UP/UV/AT/ED 等のビット OR (登録時は `0x45`)
     * @param signCount 4 byte big-endian (登録時は 0)
     * @param attestedCredentialData 省略時は null (FLAG_AT との整合は呼び出し側)
     * @param extensions CBOR map 形式の extensions (本 Issue では常に null / ED=0)
     */
    fun build(
        rpIdHash: ByteArray,
        flags: Byte,
        signCount: Int,
        attestedCredentialData: ByteArray?,
        extensions: ByteArray? = null,
    ): ByteArray {
        require(rpIdHash.size == 32) {
            "rpIdHash must be 32 bytes (SHA-256 digest), got ${rpIdHash.size}"
        }

        val signCountBytes = ByteBuffer.allocate(4)
            .order(ByteOrder.BIG_ENDIAN)
            .putInt(signCount)
            .array()

        val out = ByteArrayOutputStream()
        out.write(rpIdHash)
        out.write(byteArrayOf(flags))
        out.write(signCountBytes)
        if (attestedCredentialData != null) out.write(attestedCredentialData)
        if (extensions != null) out.write(extensions)
        return out.toByteArray()
    }
}
