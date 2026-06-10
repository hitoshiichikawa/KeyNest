package io.github.hitoshiichikawa.keynest.security

import com.google.common.truth.Truth.assertThat
import org.junit.Test

/**
 * Issue #138: [CharArrayCodec] の CharArray ⇔ UTF-8 ByteArray 変換の検証。
 * Save / Update / Unlock の各 use case に重複していた実装を集約した
 * 単一監査点（NFR 1.4）に対する直接テスト。
 */
class CharArrayCodecTest {

    @Test
    fun encodeUtf8_asciiPassword_matchesStringEncoding() {
        // Arrange
        val chars = "s3cret!pass".toCharArray()

        // Act
        val bytes = CharArrayCodec.encodeUtf8(chars)

        // Assert
        assertThat(bytes).isEqualTo("s3cret!pass".toByteArray(Charsets.UTF_8))
    }

    @Test
    fun encodeUtf8_multibyteAndSurrogatePairs_roundTripsExactly() {
        // Arrange: 日本語（3 byte）+ 絵文字（surrogate pair / 4 byte）混在
        val original = "ぱすわーど🔑漢字"
        val chars = original.toCharArray()

        // Act
        val bytes = CharArrayCodec.encodeUtf8(chars)
        val decoded = CharArrayCodec.decodeUtf8(bytes)

        // Assert
        assertThat(String(decoded)).isEqualTo(original)
        assertThat(bytes).isEqualTo(original.toByteArray(Charsets.UTF_8))
    }

    @Test
    fun encodeUtf8_emptyInput_yieldsEmptyArray() {
        // Arrange / Act
        val bytes = CharArrayCodec.encodeUtf8(CharArray(0))

        // Assert
        assertThat(bytes).isEmpty()
    }

    @Test
    fun decodeUtf8_emptyInput_yieldsEmptyArray() {
        // Arrange / Act
        val chars = CharArrayCodec.decodeUtf8(ByteArray(0))

        // Assert
        assertThat(chars).isEmpty()
    }

    @Test
    fun encodeUtf8_doesNotMutateInputChars() {
        // Arrange: 入力の wipe 責務は呼び出し側（ownership 非移動の契約）
        val chars = "keepme".toCharArray()

        // Act
        CharArrayCodec.encodeUtf8(chars)

        // Assert
        assertThat(String(chars)).isEqualTo("keepme")
    }

    @Test
    fun decodeUtf8_doesNotMutateInputBytes() {
        // Arrange
        val bytes = "keepme".toByteArray(Charsets.UTF_8)

        // Act
        CharArrayCodec.decodeUtf8(bytes)

        // Assert
        assertThat(bytes).isEqualTo("keepme".toByteArray(Charsets.UTF_8))
    }
}
