package com.example.keynest.security

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.security.KeyStore

/**
 * Round-trip and IV uniqueness tests for [AesGcmCipher].
 *
 * Runs as an instrumentation test because AndroidKeyStore is unavailable on
 * the JVM / Robolectric (Robolectric stubs do not implement the real Keystore
 * for AES-GCM key generation).
 *
 * Requirements covered:
 * - NFR 1.1 - same plaintext encrypted twice produces different ciphertexts
 * - NFR 1.1 - decrypt(encrypt(p)) == p
 * - 1.2     - the raw bytes only round-trip via the Keystore key
 */
@RunWith(AndroidJUnit4::class)
class AesGcmCipherTest {

    private val testAlias = "keynest_test_aead_v1"

    @Before
    fun clearTestKey() = deleteTestKey()

    @After
    fun cleanup() = deleteTestKey()

    private fun deleteTestKey() {
        val ks = KeyStore.getInstance(KeystoreKeyProvider.ANDROID_KEYSTORE).apply { load(null) }
        if (ks.containsAlias(testAlias)) ks.deleteEntry(testAlias)
    }

    private fun newCipher() = AesGcmCipher(KeystoreKeyProvider(keyAlias = testAlias))

    @Test
    fun encrypt_thenDecrypt_returnsOriginalPlaintext() {
        // Arrange
        val cipher = newCipher()
        val plaintext = "p@ssw0rd_secret".toByteArray(Charsets.UTF_8)

        // Act
        val blob = cipher.encrypt(plaintext)
        val recovered = cipher.decrypt(blob)

        // Assert
        assertThat(recovered).isEqualTo(plaintext)
    }

    @Test
    fun encryptingSamePlaintextTwice_producesDistinctCiphertexts() {
        // Arrange
        val cipher = newCipher()
        val plaintext = "p@ssw0rd_secret".toByteArray(Charsets.UTF_8)

        // Act
        val first = cipher.encrypt(plaintext)
        val second = cipher.encrypt(plaintext)

        // Assert: randomized encryption => unique IV => unique ciphertext bytes
        assertThat(first.iv).isNotEqualTo(second.iv)
        assertThat(first.ciphertext).isNotEqualTo(second.ciphertext)
    }

    @Test
    fun encrypt_producesTwelveByteIv() {
        // GCM standard IV size is 12 bytes; the platform should always pick this.
        val cipher = newCipher()
        val blob = cipher.encrypt(byteArrayOf(0x01, 0x02, 0x03))
        assertThat(blob.iv.size).isEqualTo(12)
    }
}
