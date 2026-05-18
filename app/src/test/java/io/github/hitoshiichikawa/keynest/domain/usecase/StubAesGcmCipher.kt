package io.github.hitoshiichikawa.keynest.domain.usecase

import io.github.hitoshiichikawa.keynest.security.AesGcmCipher
import io.github.hitoshiichikawa.keynest.security.EncryptedBlob
import io.github.hitoshiichikawa.keynest.security.KeystoreKeyProvider
import javax.crypto.SecretKey

/**
 * Test double for [AesGcmCipher] that does NOT touch the AndroidKeyStore.
 *
 * The encryption itself is a trivial XOR with a known mask. Each call
 * fabricates a distinct 12-byte IV (matching the real cipher's GCM IV
 * shape). The transform is its own inverse, so decrypt(encrypt(p)) == p.
 *
 * This makes the use-case tests run under plain JVM JUnit without
 * Robolectric while still letting us verify "ciphertext is not the
 * plaintext" assertions.
 */
internal class StubAesGcmCipher : AesGcmCipher(StubKeyProvider()) {

    private var counter: Int = 0

    override fun encrypt(plaintext: ByteArray): EncryptedBlob {
        counter++
        val iv = ByteArray(12) { (counter + it).toByte() }
        val ciphertext = ByteArray(plaintext.size) { (plaintext[it].toInt() xor 0x5A).toByte() }
        return EncryptedBlob(iv = iv, ciphertext = ciphertext)
    }

    override fun decrypt(blob: EncryptedBlob): ByteArray {
        return ByteArray(blob.ciphertext.size) { (blob.ciphertext[it].toInt() xor 0x5A).toByte() }
    }

    /** Returns how many encrypt() calls have been issued (for IV-uniqueness assertions). */
    fun encryptCount(): Int = counter

    private class StubKeyProvider : KeystoreKeyProvider() {
        override fun getOrCreateKey(): SecretKey {
            error("StubAesGcmCipher should not invoke KeystoreKeyProvider.getOrCreateKey()")
        }
    }
}
