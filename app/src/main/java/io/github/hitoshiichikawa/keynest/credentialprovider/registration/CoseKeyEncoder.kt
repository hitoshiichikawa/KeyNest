package io.github.hitoshiichikawa.keynest.credentialprovider.registration

import java.math.BigInteger
import java.security.interfaces.ECPublicKey

/**
 * ES256 (P-256) public key を RFC 8152 §13.1 / WebAuthn §6.5.1.1 の COSE_Key
 * として CBOR encode する (Issue #99 / parent #89 / design §4.4 / §7.4).
 *
 * 出力 CBOR map (5 entries):
 *
 * | label | value | 意味 |
 * |-------|-------|------|
 * | 1 (kty)  | 2 (EC2)   | Key type |
 * | 3 (alg)  | -7        | Algorithm = ES256 |
 * | -1 (crv) | 1 (P-256) | Curve |
 * | -2 (x)   | 32 byte big-endian | X coordinate |
 * | -3 (y)   | 32 byte big-endian | Y coordinate |
 *
 * map のキーは canonical CBOR encoding (length-then-byte ordering) ではなく
 * **COSE 慣例の (1, 3, -1, -2, -3) 順** で出力する。理由は WebAuthn テストベクタ
 * との bytewise 一致を保つため (design §4.4 / §7.4)。
 *
 * `BigInteger.toByteArray()` は MSB に sign bit が立つと先頭に 0x00 を prepend する
 * 癖があり、また値が 32 byte 未満なら短い byte 配列を返す。本クラスは
 * [toUnsignedFixedLength] でこれを正規化し、x / y を **必ず 32 byte 左 0 padding**
 * で書き出す (Req 6.1 (b))。
 */
internal object CoseKeyEncoder {

    private const val P256_FIELD_SIZE_BITS = 256
    private const val COORDINATE_LENGTH_BYTES = 32

    /**
     * ES256 (P-256) 公開鍵を COSE_Key (alg = -7, kty = 2, crv = 1) として CBOR encode する。
     *
     * @throws IllegalArgumentException P-256 以外の curve / 32 byte に収まらない座標
     */
    fun encodeEs256(publicKey: ECPublicKey): ByteArray {
        // 防御的: 鍵が確かに P-256 (secp256r1) であることを field size で検証する。
        // 公開鍵の curve OID 名は provider 依存だが、有限体のビット幅は P-256 で常に 256。
        val fieldSize = publicKey.params.curve.field.fieldSize
        require(fieldSize == P256_FIELD_SIZE_BITS) {
            "CoseKeyEncoder.encodeEs256 requires P-256 public key (field size 256), got $fieldSize"
        }

        val point = publicKey.w
        val xBytes = point.affineX.toUnsignedFixedLength(COORDINATE_LENGTH_BYTES)
        val yBytes = point.affineY.toUnsignedFixedLength(COORDINATE_LENGTH_BYTES)

        return CborWriter()
            .writeMapHeader(5)
            .writeUnsignedInt(1).writeUnsignedInt(2)        // kty = EC2
            .writeUnsignedInt(3).writeNegativeInt(-7)        // alg = ES256
            .writeNegativeInt(-1).writeUnsignedInt(1)        // crv = P-256
            .writeNegativeInt(-2).writeByteString(xBytes)    // x
            .writeNegativeInt(-3).writeByteString(yBytes)    // y
            .toByteArray()
    }

    /**
     * `BigInteger` を `length` byte の unsigned big-endian byte 配列に正規化する。
     *
     * - `BigInteger.toByteArray()` の sign byte (MSB 立ちで先頭に 0x00 が prepend される
     *   ケース) を剥がす
     * - 短い byte 列は左 0 padding で `length` に揃える
     * - `length` byte に収まらない値は [IllegalArgumentException]
     */
    private fun BigInteger.toUnsignedFixedLength(length: Int): ByteArray {
        require(signum() >= 0) { "BigInteger must be non-negative, got $this" }
        val raw = toByteArray()
        // BigInteger は 2's complement で、MSB が立つと先頭に 0x00 が prepend される。
        // 例: 32 byte ちょうどの値で MSB=1 のとき raw.size == 33 で raw[0] == 0x00。
        val stripped = if (raw.size > length && raw[0] == 0x00.toByte()) {
            raw.copyOfRange(1, raw.size)
        } else {
            raw
        }
        require(stripped.size <= length) {
            "BigInteger does not fit in $length bytes (actual size = ${stripped.size})"
        }
        if (stripped.size == length) return stripped
        val padded = ByteArray(length)
        System.arraycopy(stripped, 0, padded, length - stripped.size, stripped.size)
        return padded
    }
}
