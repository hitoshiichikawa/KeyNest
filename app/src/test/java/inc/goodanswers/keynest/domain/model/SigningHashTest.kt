package inc.goodanswers.keynest.domain.model

import com.google.common.truth.Truth.assertThat
import org.junit.Test

/**
 * SigningHash value-object behaviour. Backs Req 2.1, 4.1, 4.2.
 */
class SigningHashTest {

    @Test
    fun equal_when_byteArraysAreEqual() {
        val bytes = ByteArray(SigningHash.DIGEST_BYTES) { it.toByte() }
        val a = SigningHash(bytes)
        val b = SigningHash(bytes.copyOf())
        assertThat(a).isEqualTo(b)
        assertThat(a.hashCode()).isEqualTo(b.hashCode())
    }

    @Test
    fun notEqual_when_singleByteDiffers() {
        val a = SigningHash(ByteArray(SigningHash.DIGEST_BYTES) { it.toByte() })
        val mutated = ByteArray(SigningHash.DIGEST_BYTES) { it.toByte() }
        mutated[15] = (mutated[15] + 1).toByte()
        val b = SigningHash(mutated)
        assertThat(a).isNotEqualTo(b)
    }

    @Test(expected = IllegalArgumentException::class)
    fun construction_rejects_wrongLength_short() {
        SigningHash(ByteArray(31))
    }

    @Test(expected = IllegalArgumentException::class)
    fun construction_rejects_wrongLength_long() {
        SigningHash(ByteArray(33))
    }

    @Test
    fun value_returnsDefensiveCopy() {
        val original = ByteArray(SigningHash.DIGEST_BYTES) { it.toByte() }
        val h = SigningHash(original)
        val exposed = h.value
        exposed[0] = 0x7F
        // mutating exposed bytes must not leak back into the hash
        assertThat(h.value[0]).isEqualTo(0.toByte())
    }

    @Test
    fun toString_doesNotLeakRawBytes() {
        val h = SigningHash(ByteArray(SigningHash.DIGEST_BYTES) { 0x42.toByte() })
        val rendered = h.toString()
        assertThat(rendered).doesNotContain("42")
        assertThat(rendered).contains("32B")
    }

    @Test
    fun ofSha256_producesStableHash() {
        val a = SigningHash.ofSha256("hello".toByteArray())
        val b = SigningHash.ofSha256("hello".toByteArray())
        assertThat(a).isEqualTo(b)
        val c = SigningHash.ofSha256("world".toByteArray())
        assertThat(a).isNotEqualTo(c)
    }
}
