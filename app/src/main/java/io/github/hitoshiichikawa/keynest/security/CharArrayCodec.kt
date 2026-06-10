package io.github.hitoshiichikawa.keynest.security

import java.nio.ByteBuffer
import java.nio.CharBuffer
import java.nio.charset.StandardCharsets
import java.util.Arrays

/**
 * パスワード等の機密文字列を **String を経由せず** CharArray ⇔ UTF-8
 * ByteArray 変換する共有ユーティリティ（Issue #138）。
 *
 * String を経由しない理由: String は immutable で JVM ヒープ（場合に
 * よっては intern テーブル）に GC 回収まで残留し、呼び出し側が
 * zero-fill できないため（NFR 1.4）。本ユーティリティは変換の中間
 * バッファ（Charset encoder/decoder の backing array）も即座に
 * wipe する。
 *
 * 従来 SaveCredentialUseCase / UpdateCredentialUseCase / UnlockVaultUseCase
 * に同一実装が重複しており、パスワードのメモリ安全を監査する箇所が
 * 分散していた。本 object がその単一監査点になる。
 *
 * 注意: 入力（[encodeUtf8] の chars / [decodeUtf8] の bytes）と返り値の
 * wipe 責務は **呼び出し側** に残る（ownership は移動しない）。
 */
object CharArrayCodec {

    /**
     * [chars] を UTF-8 バイト列へ変換する。中間 ByteBuffer の backing
     * array は返却前に 0 で wipe する。入力 [chars] は変更しない。
     */
    fun encodeUtf8(chars: CharArray): ByteArray {
        val byteBuffer: ByteBuffer = StandardCharsets.UTF_8.encode(CharBuffer.wrap(chars))
        val out = ByteArray(byteBuffer.remaining())
        byteBuffer.get(out)
        if (byteBuffer.hasArray()) {
            Arrays.fill(
                byteBuffer.array(),
                byteBuffer.arrayOffset(),
                byteBuffer.arrayOffset() + byteBuffer.limit(),
                0,
            )
        }
        return out
    }

    /**
     * UTF-8 バイト列 [bytes] を CharArray へ変換する。中間 CharBuffer の
     * backing array は返却前に空白で wipe する。入力 [bytes] は変更しない。
     */
    fun decodeUtf8(bytes: ByteArray): CharArray {
        val charBuffer = StandardCharsets.UTF_8.decode(ByteBuffer.wrap(bytes))
        val out = CharArray(charBuffer.remaining())
        charBuffer.get(out)
        if (charBuffer.hasArray()) {
            Arrays.fill(
                charBuffer.array(),
                charBuffer.arrayOffset(),
                charBuffer.arrayOffset() + charBuffer.limit(),
                ' ',
            )
        }
        return out
    }
}
