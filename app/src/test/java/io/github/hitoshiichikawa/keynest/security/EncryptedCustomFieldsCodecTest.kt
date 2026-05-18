package io.github.hitoshiichikawa.keynest.security

import com.google.common.truth.Truth.assertThat
import io.github.hitoshiichikawa.keynest.domain.model.CustomField
import javax.crypto.SecretKey
import org.junit.Test

/**
 * Behaviour of [EncryptedCustomFieldsCodec]. Issue #66 Phase 1.
 *
 * Backs Req 6.6 (encryption round-trip), Req 5.1 (no plaintext leakage),
 * design.md §6.1 (empty BLOB fallback) and §5.4 (JSON parse fail-open).
 *
 * The cipher under test is a JVM-friendly XOR stub that captures the input
 * to encrypt so tests can verify the intermediate UTF-8 buffer was zero
 * filled before the codec returns.
 */
class EncryptedCustomFieldsCodecTest {

    @Test
    fun encrypt_then_decrypt_roundTripsList() {
        val cipher = SpyAesGcmCipher()
        val codec = EncryptedCustomFieldsCodec(cipher)
        val original = listOf(
            CustomField("会員番号", "M-12345"),
            CustomField("storeCode", "TKO-001"),
            CustomField("contractId", "9876-XYZ"),
        )

        val blob = codec.encrypt(original)
        val roundTripped = codec.decrypt(blob)

        assertThat(roundTripped).containsExactlyElementsIn(original).inOrder()
    }

    @Test
    fun encrypt_emptyList_yieldsNonEmptyBlob() {
        // The codec serialises `[]` as JSON, so even an empty list produces
        // a non-empty ciphertext. This keeps the persistence shape uniform.
        val codec = EncryptedCustomFieldsCodec(SpyAesGcmCipher())

        val blob = codec.encrypt(emptyList())

        assertThat(blob.ciphertext).isNotEmpty()
        assertThat(blob.iv).hasLength(12)
    }

    @Test
    fun decrypt_emptyCiphertext_returnsEmptyList_withoutCallingCipher() {
        // Migration-default rows have a zero-length ciphertext / IV pair.
        // The codec must treat this as "no fields" and NOT attempt
        // AES-GCM decryption (which would throw on empty input).
        val cipher = ThrowingCipher()
        val codec = EncryptedCustomFieldsCodec(cipher)

        val result = codec.decrypt(EncryptedBlob(iv = ByteArray(0), ciphertext = ByteArray(0)))

        assertThat(result).isEmpty()
        assertThat(cipher.decryptCalls).isEqualTo(0)
    }

    @Test
    fun decrypt_invalidJson_returnsEmptyList_fallback() {
        // Simulate a corruption of the encrypted JSON: the decrypted bytes
        // are not valid UTF-8 JSON. fail-open per design.md §5.4.
        val cipher = object : AesGcmCipher(NoopKeyProvider()) {
            override fun encrypt(plaintext: ByteArray): EncryptedBlob =
                EncryptedBlob(iv = ByteArray(12), ciphertext = plaintext)
            override fun decrypt(blob: EncryptedBlob): ByteArray =
                "this is not json".toByteArray(Charsets.UTF_8)
        }
        val codec = EncryptedCustomFieldsCodec(cipher)

        val result = codec.decrypt(EncryptedBlob(iv = ByteArray(12), ciphertext = ByteArray(4) { 1 }))

        assertThat(result).isEmpty()
    }

    @Test
    fun encrypt_zeroFillsIntermediateUtf8Buffer() {
        // Req 5.1 / NFR 1.4: the codec must wipe the intermediate UTF-8
        // bytes before returning so the plaintext does not linger in heap.
        val cipher = SpyAesGcmCipher()
        val codec = EncryptedCustomFieldsCodec(cipher)

        codec.encrypt(listOf(CustomField("memberId", "leaky-secret-12345")))

        val captured = cipher.lastEncryptInput
        assertThat(captured).isNotNull()
        // After encrypt returns, the codec wiped the buffer it handed in.
        // SpyAesGcmCipher captures the reference (not a copy), so the wipe
        // is observable here.
        for (b in captured!!) {
            assertThat(b).isEqualTo(0.toByte())
        }
    }

    @Test
    fun encrypt_iv_isFresh_perCall() {
        // Sanity: the underlying cipher hands us a distinct IV on every
        // encrypt(). The codec does not generate IVs itself.
        val cipher = SpyAesGcmCipher()
        val codec = EncryptedCustomFieldsCodec(cipher)

        val a = codec.encrypt(listOf(CustomField("k", "1")))
        val b = codec.encrypt(listOf(CustomField("k", "1")))

        assertThat(a.iv.toList()).isNotEqualTo(b.iv.toList())
    }

    // ---- helpers ----------------------------------------------------------

    /**
     * Test cipher that XORs the plaintext with 0x5A. The transform is its
     * own inverse so decrypt(encrypt(p)) == p. The byte-array reference
     * supplied to [encrypt] is captured (not copied) so tests can observe
     * the codec's post-call wipe.
     */
    private class SpyAesGcmCipher : AesGcmCipher(NoopKeyProvider()) {
        private var counter = 0
        var lastEncryptInput: ByteArray? = null
            private set

        override fun encrypt(plaintext: ByteArray): EncryptedBlob {
            counter++
            lastEncryptInput = plaintext
            val iv = ByteArray(12) { (counter + it).toByte() }
            val ciphertext = ByteArray(plaintext.size) { (plaintext[it].toInt() xor 0x5A).toByte() }
            return EncryptedBlob(iv = iv, ciphertext = ciphertext)
        }

        override fun decrypt(blob: EncryptedBlob): ByteArray =
            ByteArray(blob.ciphertext.size) { (blob.ciphertext[it].toInt() xor 0x5A).toByte() }
    }

    private class ThrowingCipher : AesGcmCipher(NoopKeyProvider()) {
        var decryptCalls: Int = 0
            private set

        override fun decrypt(blob: EncryptedBlob): ByteArray {
            decryptCalls++
            error("decrypt() must not be called on an empty BLOB")
        }
    }

    private class NoopKeyProvider : KeystoreKeyProvider() {
        override fun getOrCreateKey(): SecretKey =
            error("Stub cipher does not require a Keystore key")
    }
}
