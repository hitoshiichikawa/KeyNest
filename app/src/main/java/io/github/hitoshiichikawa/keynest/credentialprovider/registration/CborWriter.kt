package io.github.hitoshiichikawa.keynest.credentialprovider.registration

import java.io.ByteArrayOutputStream

/**
 * 本 Issue #99 (parent #89) で必要な CBOR primitive のみを encode する minimal writer。
 *
 * RFC 8949 §3 (Concise Binary Object Representation) の major type 0/1/2/3/4/5 を
 * サポートする。本 Issue で実際に使う primitive は以下:
 *
 * - major type 0 (unsigned int): COSE_Key の kty / alg / crv 値、attestedCredentialData の長さ
 * - major type 1 (negative int): COSE_Key の alg = -7 / crv key = -1 / x = -2 / y = -3
 * - major type 2 (byte string): COSE_Key の x / y, attestationObject の authData
 * - major type 3 (text string): attestationObject の "fmt" / "attStmt" / "authData", "none"
 * - major type 4 (array header): 本 Issue では未使用だが認証セレモニー (#89 分割案 4) で
 *   再利用するため API として提供
 * - major type 5 (map header): COSE_Key map(5) / attestationObject map(3) / attStmt map(0)
 *
 * Length encoding (RFC 8949 §3):
 * - `0..23`        → 1 byte (`major<<5 | value`)
 * - `24..255`      → `major<<5 | 0x18`, 1 byte
 * - `256..65535`   → `major<<5 | 0x19`, 2 byte big-endian
 * - `65536..2^32-1`→ `major<<5 | 0x1A`, 4 byte big-endian
 * - `2^32..2^64-1` → `major<<5 | 0x1B`, 8 byte big-endian
 *
 * 外部 CBOR ライブラリ依存は追加しない方針 (design §7.5)。本クラスは canonical CBOR
 * encoding (length-then-byte ordering) を強制せず、呼び出し側が WebAuthn / COSE 慣例
 * の map ordering を制御できるようにする (design §4.4 / §4.6)。
 */
internal class CborWriter {

    private val buffer = ByteArrayOutputStream()

    /**
     * Major type 0: non-negative integer。
     * @throws IllegalArgumentException 引数が負値の場合
     */
    fun writeUnsignedInt(value: Long): CborWriter {
        require(value >= 0) { "writeUnsignedInt requires non-negative value, got $value" }
        writeHeader(MAJOR_UNSIGNED, value)
        return this
    }

    /**
     * Major type 1: negative integer。引数 `value` は **負の Long** を取り、内部で
     * `-1L - value` の unsigned representation を書き出す (RFC 8949 §3.1)。
     * 例: `writeNegativeInt(-1)` → `0x20` (major 1, argument 0 = -1 - 0)。
     * @throws IllegalArgumentException 引数が 0 以上の場合
     */
    fun writeNegativeInt(value: Long): CborWriter {
        require(value < 0) { "writeNegativeInt requires negative value, got $value" }
        writeHeader(MAJOR_NEGATIVE, -1L - value)
        return this
    }

    /** Major type 2: byte string。 */
    fun writeByteString(bytes: ByteArray): CborWriter {
        writeHeader(MAJOR_BYTE_STRING, bytes.size.toLong())
        buffer.write(bytes, 0, bytes.size)
        return this
    }

    /** Major type 3: text string (UTF-8 byte length, not codepoint count)。 */
    fun writeTextString(s: String): CborWriter {
        val utf8 = s.toByteArray(Charsets.UTF_8)
        writeHeader(MAJOR_TEXT_STRING, utf8.size.toLong())
        buffer.write(utf8, 0, utf8.size)
        return this
    }

    /** Major type 4: array header (subsequent N items are written separately by the caller)。 */
    fun writeArrayHeader(count: Int): CborWriter {
        require(count >= 0) { "writeArrayHeader requires non-negative count, got $count" }
        writeHeader(MAJOR_ARRAY, count.toLong())
        return this
    }

    /** Major type 5: map header (subsequent N key/value pairs are written separately by the caller)。 */
    fun writeMapHeader(entryCount: Int): CborWriter {
        require(entryCount >= 0) { "writeMapHeader requires non-negative entryCount, got $entryCount" }
        writeHeader(MAJOR_MAP, entryCount.toLong())
        return this
    }

    /** 現在の buffer 内容を新規 byte 配列で返す。 */
    fun toByteArray(): ByteArray = buffer.toByteArray()

    /**
     * RFC 8949 §3 に従う head の書き出し。`majorType` は 0..7、`argument` は非負の
     * 64-bit unsigned 数値を Long で表現したもの (2^63 以上の値は本 writer 範囲外)。
     */
    private fun writeHeader(majorType: Int, argument: Long) {
        val prefix = majorType shl 5
        when {
            argument < 0L -> error("CBOR argument must be non-negative, got $argument")
            argument <= 23L -> {
                buffer.write(prefix or argument.toInt())
            }
            argument <= 0xFFL -> {
                buffer.write(prefix or 0x18)
                buffer.write(argument.toInt() and 0xFF)
            }
            argument <= 0xFFFFL -> {
                buffer.write(prefix or 0x19)
                buffer.write((argument ushr 8).toInt() and 0xFF)
                buffer.write(argument.toInt() and 0xFF)
            }
            argument <= 0xFFFFFFFFL -> {
                buffer.write(prefix or 0x1A)
                buffer.write((argument ushr 24).toInt() and 0xFF)
                buffer.write((argument ushr 16).toInt() and 0xFF)
                buffer.write((argument ushr 8).toInt() and 0xFF)
                buffer.write(argument.toInt() and 0xFF)
            }
            else -> {
                buffer.write(prefix or 0x1B)
                buffer.write((argument ushr 56).toInt() and 0xFF)
                buffer.write((argument ushr 48).toInt() and 0xFF)
                buffer.write((argument ushr 40).toInt() and 0xFF)
                buffer.write((argument ushr 32).toInt() and 0xFF)
                buffer.write((argument ushr 24).toInt() and 0xFF)
                buffer.write((argument ushr 16).toInt() and 0xFF)
                buffer.write((argument ushr 8).toInt() and 0xFF)
                buffer.write(argument.toInt() and 0xFF)
            }
        }
    }

    private companion object {
        const val MAJOR_UNSIGNED = 0
        const val MAJOR_NEGATIVE = 1
        const val MAJOR_BYTE_STRING = 2
        const val MAJOR_TEXT_STRING = 3
        const val MAJOR_ARRAY = 4
        const val MAJOR_MAP = 5
    }
}
