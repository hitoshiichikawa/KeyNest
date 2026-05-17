package io.github.hitoshiichikawa.keynest.util

/**
 * Lowercase hex encoding / decoding for binary identifiers (notably SHA-256
 * hashes of package signing certificates).
 *
 * Requirements: 2.1 (signing hash storage / display).
 *
 * We use a hand-rolled implementation to keep the API JVM 8 compatible (the
 * `java.util.HexFormat` class only exists on JVM 17, which is fine for compile
 * but not for Android runtime where this code may be unit-tested on the host
 * JDK without ART). For test environments we always pin to lowercase hex.
 */
object HexEncoding {

    private val HEX_CHARS = "0123456789abcdef".toCharArray()

    /** ByteArray -> lowercase hex string (length == 2 * input). */
    fun encode(bytes: ByteArray): String {
        val out = CharArray(bytes.size * 2)
        for (i in bytes.indices) {
            val v = bytes[i].toInt() and 0xFF
            out[i * 2] = HEX_CHARS[v ushr 4]
            out[i * 2 + 1] = HEX_CHARS[v and 0x0F]
        }
        return String(out)
    }

    /**
     * Lowercase or uppercase hex string -> ByteArray.
     * @throws IllegalArgumentException if [hex] length is odd or contains
     *   non-hex characters.
     */
    fun decode(hex: String): ByteArray {
        require(hex.length % 2 == 0) { "hex string must have even length: ${hex.length}" }
        val out = ByteArray(hex.length / 2)
        for (i in out.indices) {
            val hi = hexValue(hex[i * 2])
            val lo = hexValue(hex[i * 2 + 1])
            out[i] = ((hi shl 4) or lo).toByte()
        }
        return out
    }

    private fun hexValue(c: Char): Int = when (c) {
        in '0'..'9' -> c - '0'
        in 'a'..'f' -> 10 + (c - 'a')
        in 'A'..'F' -> 10 + (c - 'A')
        else -> throw IllegalArgumentException("non-hex character: $c")
    }
}
